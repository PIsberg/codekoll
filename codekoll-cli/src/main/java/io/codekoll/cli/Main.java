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
import io.codekoll.workspace.ConfigException;
import io.codekoll.workspace.ConfigLoader;
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

  // Nullable rather than defaulted: "not given on the command line" has to stay distinguishable
  // from "given the same value the default happens to have", or a config file could never win.
  @Option(names = "--no-tests", description = "Skip test source sets.")
  private @Nullable Boolean noTests;

  @Option(names = "--no-gitignore", description = "Do not honour .gitignore.")
  private @Nullable Boolean noGitignore;

  @Option(names = "--classpath", paramLabel = "<cp>",
      description = "Classpath appended to every unit's resolved classpath.")
  private @Nullable String classpath;

  @Option(names = "--resolve", paramLabel = "<mode>",
      description = "Classpath strategy: discover, none (default: discover). "
          + "build and auto arrive with the trust gate.")
  private @Nullable String resolve;

  @Option(names = "--release", paramLabel = "<n>",
      description = "Override the detected language level for all units.")
  private @Nullable Integer release;

  @Option(names = "--format", paramLabel = "<format>",
      description = "Output format: console, json, sarif (default: console).")
  private @Nullable String format;

  @Option(names = "--fail-on", paramLabel = "<level>",
      description = "Exit-code threshold: error, warning, never (default: error).")
  private @Nullable String failOn;

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
  private @Nullable Boolean absolutePaths;

  @Option(names = "--verbose",
      description = "Print the workspace header and every discovery diagnostic.")
  private boolean verbose;

  @Option(names = "--config", paramLabel = "<file>",
      description = "Read this config file instead of the repository's codekoll.toml.")
  private @Nullable Path configFile;

  @Option(names = "--print-config",
      description = "Print the effective configuration and where each value came from, then exit.")
  private boolean printConfig;

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
    List<Rule> allRules = RuleRegistry.loadAll();
    if (catalog) {
      printCatalog(allRules, out);
      return 0;
    }
    if (!explain.isEmpty()) {
      return explainRule(allRules, out);
    }

    Settings settings;
    List<String> configDiagnostics = new ArrayList<>();
    try {
      Path repoRoot = WorkspaceDiscovery.repoRootFor(paths, repo);
      settings = new Settings(
          ConfigLoader.load(repoRoot, configFile, userConfigDir(), configDiagnostics));
    } catch (ConfigException e) {
      out.println(e.getMessage());
      return 2;
    }

    if (printConfig) {
      printConfig(settings, out);
      return 0;
    }

    Workspace workspace;
    List<Rule> rules;
    Map<String, Severity> severityOverrides;
    try {
      workspace = new WorkspaceDiscovery(options(settings)).discover(paths);
      rules = settings.select(allRules, ruleIds, packs);
      severityOverrides = settings.severityOverrides(allRules);
    } catch (ConfigException | IllegalArgumentException e) {
      out.println(e.getMessage());
      return 2;
    }

    if (printWorkspace) {
      printWorkspace(workspace, out);
      return 0;
    }
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

    AnalysisResult result = applyOverrides(analyze(workspace, rules), severityOverrides);
    reporter(workspace, settings).report(result.findings(), out);
    reportDiagnostics(workspace, result);
    configDiagnostics.forEach(this::warn);
    settings.notes().forEach(this::warn);
    return exitCode(result, settings);
  }

  /**
   * Re-stamps findings whose rule has a {@code [severity]} override.
   *
   * <p>Applied before reporting <em>and</em> before {@link #exitCode}: an override that changed
   * only the printed word, while the exit code still followed the rule's default, would be a
   * setting that looks obeyed and is not.
   */
  private static AnalysisResult applyOverrides(AnalysisResult result,
      Map<String, Severity> overrides) {
    if (overrides.isEmpty()) {
      return result;
    }
    List<Finding> restamped = result.findings().stream()
        .map(f -> {
          Severity override = overrides.get(f.rule().value());
          return override == null || override == f.severity() ? f
              : new Finding(f.rule(), override, f.file(), f.line(), f.column(), f.message(),
                  f.snippet());
        })
        .toList();
    return new AnalysisResult(restamped, result.skippedFiles(), result.ruleFailures());
  }

  /**
   * The user's config directory, or {@code null} when there is none to read.
   *
   * <p>{@code XDG_CONFIG_HOME} first so that a caller can point codekoll somewhere reproducible;
   * this is the only configuration codekoll reads from outside the repository, and it is also the
   * only place allowed to enable build execution (CLI-SPEC §4.3).
   */
  private static @Nullable Path userConfigDir() {
    String xdg = System.getenv("XDG_CONFIG_HOME");
    if (xdg != null && !xdg.isBlank()) {
      return Path.of(xdg);
    }
    String home = System.getProperty("user.home");
    return home == null || home.isBlank() ? null : Path.of(home, ".config");
  }

  private WorkspaceOptions options(Settings settings) {
    return new WorkspaceOptions(repo,
        settings.union("sources.include", includes),
        settings.union("sources.exclude", concat(excludes, settings.list("suppress.paths",
            List.of()))),
        settings.flag("sources.tests", noTests == null ? null : !noTests, true),
        settings.flag("sources.gitignore", noGitignore == null ? null : !noGitignore, true),
        settings.integer("compile.release", release, 0),
        resolveMode(settings),
        settings.string("compile.classpath", classpath, ""),
        WorkspaceOptions.DEFAULT_MAX_FILE_BYTES);
  }

  private static List<String> concat(List<String> first, List<String> second) {
    List<String> all = new ArrayList<>(first);
    all.addAll(second);
    return all;
  }

  /**
   * {@code build} and {@code auto} parse but are refused: both invoke the target repository's own
   * build tool, which is gated on the trust rules of CLI-SPEC §4.3 and does not exist yet.
   * Accepting them silently as {@code discover} would be the quiet failure that gate exists to
   * prevent.
   */
  private ResolveMode resolveMode(Settings settings) {
    String requested = settings.string("resolve.mode", resolve, "discover");
    ResolveMode mode = ResolveMode.parse(requested);
    if (mode == ResolveMode.BUILD || mode == ResolveMode.AUTO) {
      throw new IllegalArgumentException(
          "resolve mode '" + requested + "' is not implemented yet; use discover or none, or pass "
              + "--classpath explicitly");
    }
    return mode;
  }

  private PathRenderer pathRenderer(Workspace workspace, Settings settings) {
    return settings.flag("report.absolute-paths", absolutePaths, false)
        ? PathRenderer.absolute()
        : workspace::relativize;
  }

  private Reporter reporter(Workspace workspace, Settings settings) {
    PathRenderer renderer = pathRenderer(workspace, settings);
    return switch (settings.string("report.format", format, "console")) {
      case "json" -> new JsonReporter(renderer);
      case "sarif" -> new SarifReporter(renderer);
      default -> new ConsoleReporter(renderer);
    };
  }

  /** Effective settings that {@code --print-config} exists to make visible. */
  private void printConfig(Settings settings, PrintWriter out) {
    if (settings.values().isEmpty()) {
      out.println("No configuration file in effect; every setting is a built-in default or a "
          + "command-line flag.");
      return;
    }
    out.println("key                              value                          from");
    settings.values().forEach((key, value) ->
        out.printf("%-32s %-30s %s%n", key, render(value.value()), value.origin()));
    out.flush();
  }

  private static String render(Object value) {
    return value instanceof List<?> list
        ? list.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ",
            "[", "]"))
        : String.valueOf(value);
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

  private int exitCode(AnalysisResult result, Settings settings) {
    return switch (settings.string("report.fail-on", failOn, "error")) {
      case "never" -> 0;
      case "warning" -> result.findings().stream()
          .anyMatch(f -> f.severity() != Severity.INFO) ? 1 : 0;
      default -> result.hasErrors() ? 1 : 0;
    };
  }

  /** Stderr, so that {@code --output} keeps carrying nothing but the report. */
  @SuppressWarnings("PMD.SystemPrintln")
  private void warn(String message) {
    if (verbose || !message.contains("no codekoll.toml")) {
      System.err.println(message);
    }
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
