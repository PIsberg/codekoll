package io.codekoll.cli;

import io.codekoll.api.Finding;
import io.codekoll.api.Rule;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.engine.AnalysisResult;
import io.codekoll.engine.CompilationDriver;
import io.codekoll.engine.RuleRegistry;
import io.codekoll.report.ConsoleReporter;
import io.codekoll.report.JsonReporter;
import io.codekoll.report.PathRenderer;
import io.codekoll.report.Reporter;
import io.codekoll.report.SarifReporter;
import io.codekoll.workspace.ResolveMode;
import io.codekoll.workspace.SourceUnit;
import io.codekoll.workspace.Workspace;
import io.codekoll.workspace.WorkspaceDiscovery;
import io.codekoll.workspace.WorkspaceOptions;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Codekoll: finds Java bugs that compile cleanly but fail at runtime. */
@Command(name = "codekoll", mixinStandardHelpOptions = true,
    versionProvider = Main.Version.class,
    description = "Static analyzer for Java source: finds bugs that compile cleanly "
        + "but fail at runtime.")
public final class Main implements Callable<Integer> {

  @Parameters(arity = "0..*", paramLabel = "<path>",
      description = "Source files or directories (default: the current directory).")
  private List<Path> paths = List.of();

  @Option(names = "--repo", paramLabel = "<dir>",
      description = "Repo root that reported paths are relative to (default: detected).")
  private @Nullable Path repo;

  @Option(names = "--include", paramLabel = "<glob>",
      description = "Only analyze discovered files matching this glob (repeatable).")
  private List<String> includes = List.of();

  @Option(names = "--exclude", paramLabel = "<glob>",
      description = "Drop discovered files matching this glob (repeatable).")
  private List<String> excludes = List.of();

  @Option(names = "--no-tests", description = "Skip test source sets.")
  private boolean noTests;

  @Option(names = "--no-gitignore", description = "Do not honour .gitignore.")
  private boolean noGitignore;

  @Option(names = "--classpath", defaultValue = "",
      description = "Classpath appended to every unit's resolved classpath.")
  private String classpath = "";

  @Option(names = "--resolve", paramLabel = "<mode>", defaultValue = "discover",
      description = "Classpath strategy: discover, none (default: ${DEFAULT-VALUE}). "
          + "build and auto arrive with the trust gate.")
  private String resolve = "discover";

  @Option(names = "--release", paramLabel = "<n>",
      description = "Override the detected language level for all units.")
  private int release;

  @Option(names = "--format", defaultValue = "console",
      description = "Output format: console, json, sarif (default: ${DEFAULT-VALUE}).")
  private String format = "console";

  @Option(names = "--fail-on", defaultValue = "error",
      description = "Exit-code threshold: error, warning, never (default: ${DEFAULT-VALUE}).")
  private String failOn = "error";

  @Option(names = "--rules", split = ",", paramLabel = "<id>",
      description = "Only run these rule ids.")
  private Set<String> ruleIds = Set.of();

  @Option(names = "--packs", split = ",", paramLabel = "<pack>",
      description = "Only run these packs.")
  private Set<String> packs = Set.of();

  @Option(names = "--explain", paramLabel = "<id>",
      description = "Print a rule's explanation and fix, then exit.")
  private String explain = "";

  @Option(names = "--catalog",
      description = "Print the full rule catalog as Markdown, then exit.")
  private boolean catalog;

  @Option(names = "--print-workspace",
      description = "Print the detected repo root, build system and units, then exit.")
  private boolean printWorkspace;

  @Option(names = "--absolute-paths",
      description = "Report absolute paths instead of repo-relative ones.")
  private boolean absolutePaths;

  @Option(names = "--verbose",
      description = "Print the workspace header and every discovery diagnostic.")
  private boolean verbose;

  @Option(names = "--output", paramLabel = "<file>",
      description = "Write output to this file instead of stdout.")
  private @Nullable Path output;

  // The CLI is the one component allowed to write to stdout/stderr (ArchUnit-enforced);
  // this startup error precedes the output writer's creation.
  @Override
  @SuppressWarnings("PMD.SystemPrintln")
  public Integer call() {
    PrintWriter out;
    try {
      out = output != null
          ? new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))
          : new PrintWriter(System.out, true, StandardCharsets.UTF_8);
    } catch (IOException e) {
      System.err.println("Cannot write to " + output + ": " + e.getMessage());
      return 2;
    }
    try (out) {
      return run(out);
    }
  }

  private int run(PrintWriter out) {
    List<Rule> rules = RuleRegistry.loadAll();
    if (catalog) {
      printCatalog(rules, out);
      return 0;
    }
    if (!explain.isEmpty()) {
      return explainRule(rules, out);
    }

    WorkspaceOptions options;
    try {
      options = options();
    } catch (IllegalArgumentException e) {
      out.println(e.getMessage());
      return 2;
    }
    Workspace workspace = new WorkspaceDiscovery(options).discover(paths);
    if (printWorkspace) {
      printWorkspace(workspace, out);
      return 0;
    }

    rules = RuleRegistry.filterByPacks(RuleRegistry.filterByIds(rules, ruleIds), packs);
    if (rules.isEmpty()) {
      out.println("No rules selected.");
      return 2;
    }
    if (workspace.units().isEmpty()) {
      // Nothing to analyze is a fact worth stating: silence here reads as "clean".
      out.println("No Java sources found under " + workspace.repoRoot()
          + ". Run with --print-workspace to see what discovery decided.");
      reportDiagnostics(workspace, new AnalysisResult(List.of(), Map.of(), List.of()));
      return 0;
    }

    AnalysisResult result = analyze(workspace, rules);
    reporter(workspace).report(result.findings(), out);
    reportDiagnostics(workspace, result);
    return exitCode(result);
  }

  private WorkspaceOptions options() {
    return new WorkspaceOptions(repo, includes, excludes, !noTests, !noGitignore, release,
        resolveMode(), classpath, WorkspaceOptions.DEFAULT_MAX_FILE_BYTES);
  }

  /**
   * {@code build} and {@code auto} parse but are refused: both invoke the target repository's own
   * build tool, which is gated on the trust rules of CLI-SPEC §4.3 and does not exist yet.
   * Accepting them silently as {@code discover} would be the quiet failure that gate exists to
   * prevent.
   */
  private ResolveMode resolveMode() {
    ResolveMode mode = ResolveMode.parse(resolve);
    if (mode == ResolveMode.BUILD || mode == ResolveMode.AUTO) {
      throw new IllegalArgumentException(
          "--resolve " + resolve + " is not implemented yet; use discover or none, or pass "
              + "--classpath explicitly");
    }
    return mode;
  }

  private PathRenderer pathRenderer(Workspace workspace) {
    return absolutePaths ? PathRenderer.absolute() : workspace::relativize;
  }

  private Reporter reporter(Workspace workspace) {
    PathRenderer renderer = pathRenderer(workspace);
    return switch (format) {
      case "json" -> new JsonReporter(renderer);
      case "sarif" -> new SarifReporter(renderer);
      default -> new ConsoleReporter(renderer);
    };
  }

  /**
   * Analyzes each source unit at its own language level and with its own classpath, then merges.
   *
   * <p>One {@code javac} invocation per unit, not one for the whole repository: a multi-module
   * repo has modules at different releases and with different dependencies, and compiling them
   * together attributes neither correctly. Batching within a unit, the run timeout and the
   * attribution accounting of CLI-SPEC §5–§6 are still to come.
   */
  private AnalysisResult analyze(Workspace workspace, List<Rule> rules) {
    List<Finding> findings = new ArrayList<>();
    Map<Path, String> skipped = new LinkedHashMap<>();
    List<String> ruleFailures = new ArrayList<>();
    for (SourceUnit unit : workspace.units()) {
      AnalysisResult unitResult =
          new CompilationDriver(unit.release(), unit.classpathString())
              .analyzePaths(unit.files(), rules);
      findings.addAll(unitResult.findings());
      skipped.putAll(unitResult.skippedFiles());
      ruleFailures.addAll(unitResult.ruleFailures());
    }
    // Sorted once at the end: output order must not depend on how discovery split the repo.
    findings.sort(Comparator.comparing((Finding f) -> f.file().toString())
        .thenComparingLong(Finding::line)
        .thenComparingLong(Finding::column)
        .thenComparing(f -> f.rule().value()));
    return new AnalysisResult(findings, skipped, ruleFailures);
  }

  /**
   * Diagnostics go to stderr, never to the reporter's stream. With {@code --output} that stream
   * carries a machine-readable payload, and trailing prose after the closing bracket makes the
   * JSON unparseable and the SARIF unusable for GitHub code scanning. Stderr keeps them visible
   * in a terminal (the "fail toward visible" rule) without corrupting the artifact.
   */
  @SuppressWarnings("PMD.SystemPrintln")
  private void reportDiagnostics(Workspace workspace, AnalysisResult result) {
    if (verbose) {
      System.err.println("repo root: " + workspace.repoRoot()
          + " (" + workspace.buildSystem().label() + ", " + workspace.units().size()
          + " unit(s), " + workspace.fileCount() + " file(s))");
    }
    for (String diagnostic : workspace.diagnostics()) {
      System.err.println("workspace: " + diagnostic);
    }
    for (Map.Entry<Path, String> skip : result.skippedFiles().entrySet()) {
      System.err.println("skipped (does not compile): " + workspace.relativize(skip.getKey())
          + " — " + skip.getValue());
    }
    result.ruleFailures().forEach(f -> System.err.println("internal rule failure: " + f));
  }

  /**
   * Prints what discovery decided, so that a wrong answer is visible before it becomes a wrong
   * report. Human-readable by default; {@code --format json} emits the machine-readable form the
   * fixture-repository tests assert against.
   */
  private void printWorkspace(Workspace workspace, PrintWriter out) {
    if ("json".equals(format)) {
      printWorkspaceJson(workspace, out);
      return;
    }
    out.println("repo root:    " + workspace.repoRoot());
    out.println("build system: " + workspace.buildSystem().label());
    out.println("units:        " + workspace.units().size()
        + " (" + workspace.fileCount() + " file(s))");
    out.println();
    for (SourceUnit unit : workspace.units()) {
      out.printf("  %-28s release %-3d %-10s %4d file(s)  %d classpath entr%s%n",
          unit.name(), unit.release(),
          unit.releaseDetected() ? "(detected)" : "(guessed)",
          unit.files().size(), unit.classpath().size(),
          unit.classpath().size() == 1 ? "y" : "ies");
      for (Path root : unit.sourceRoots()) {
        out.println("      " + workspace.relativize(root));
      }
    }
    if (!workspace.diagnostics().isEmpty()) {
      out.println();
      out.println("diagnostics:");
      workspace.diagnostics().forEach(d -> out.println("  - " + d));
    }
    out.flush();
  }

  private void printWorkspaceJson(Workspace workspace, PrintWriter out) {
    out.println("{");
    out.printf("  \"repoRoot\": \"%s\",%n", escape(workspace.repoRoot().toString()));
    out.printf("  \"buildSystem\": \"%s\",%n", workspace.buildSystem());
    out.println("  \"units\": [");
    List<SourceUnit> units = workspace.units();
    for (int i = 0; i < units.size(); i++) {
      SourceUnit unit = units.get(i);
      out.printf("    {\"name\": \"%s\", \"buildSystem\": \"%s\", \"release\": %d, "
              + "\"releaseDetected\": %b, \"files\": %d, \"sourceRoots\": [%s]}%s%n",
          escape(unit.name()), unit.buildSystem(), unit.release(), unit.releaseDetected(),
          unit.files().size(), jsonPaths(workspace, unit.sourceRoots()),
          i < units.size() - 1 ? "," : "");
    }
    out.println("  ],");
    out.printf("  \"diagnostics\": [%s]%n", jsonStrings(workspace.diagnostics()));
    out.println("}");
    out.flush();
  }

  private static String jsonPaths(Workspace workspace, List<Path> paths) {
    return jsonStrings(paths.stream().map(workspace::relativize).toList());
  }

  private static String jsonStrings(List<String> values) {
    StringBuilder sb = new StringBuilder(values.size() * 24);
    for (String value : values) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append('"').append(escape(value)).append('"');
    }
    return sb.toString();
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private int explainRule(List<Rule> rules, PrintWriter out) {
    for (Rule rule : rules) {
      if (rule.id().value().equals(explain)) {
        out.println(rule.id() + " [" + rule.pack().id() + ", " + rule.defaultSeverity() + "]");
        out.println();
        out.println(rule.description());
        out.println();
        out.println("What is wrong: " + rule.explanation());
        out.println();
        out.println("How to fix:    " + rule.fix());
        return 0;
      }
    }
    out.println("Unknown rule id: " + explain);
    return 2;
  }

  /** Emits the rule catalog grouped by pack as a Markdown table, generated from metadata. */
  private void printCatalog(List<Rule> rules, PrintWriter out) {
    out.println("# Codekoll rule catalog");
    out.println();
    out.println("Generated from rule metadata — " + rules.size() + " rules.");
    out.println();
    Map<RulePack, List<Rule>> byPack = new EnumMap<>(RulePack.class);
    for (Rule rule : rules) {
      byPack.computeIfAbsent(rule.pack(), k -> new ArrayList<>()).add(rule);
    }
    for (var entry : byPack.entrySet()) {
      out.println("## " + entry.getKey().id() + " (" + entry.getValue().size() + ")");
      out.println();
      out.println("| Rule | Severity | What is wrong |");
      out.println("|------|----------|---------------|");
      for (Rule rule : entry.getValue()) {
        out.printf("| `%s` | %s | %s |%n",
            rule.id(), rule.defaultSeverity(), rule.description());
      }
      out.println();
    }
  }

  private int exitCode(AnalysisResult result) {
    return switch (failOn) {
      case "never" -> 0;
      case "warning" -> result.findings().stream()
          .anyMatch(f -> f.severity() != Severity.INFO) ? 1 : 0;
      default -> result.hasErrors() ? 1 : 0;
    };
  }

  public static void main(String[] args) {
    System.exit(new CommandLine(new Main()).execute(args));
  }

  static final class Version implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() {
      String v = Main.class.getPackage().getImplementationVersion();
      return new String[] {"codekoll " + (v == null ? "dev" : v)};
    }
  }
}
