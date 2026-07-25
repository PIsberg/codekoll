package io.codekoll.cli;

import io.codekoll.api.Rule;
import io.codekoll.api.Severity;
import io.codekoll.engine.AnalysisResult;
import io.codekoll.engine.CompilationDriver;
import io.codekoll.engine.RuleRegistry;
import io.codekoll.report.ConsoleReporter;
import io.codekoll.report.JsonReporter;
import io.codekoll.report.Reporter;
import io.codekoll.report.SarifReporter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
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

  @Parameters(arity = "1..*", paramLabel = "<path>", description = "Source files or directories.")
  private List<Path> paths = List.of();

  @Option(names = "--classpath", defaultValue = "",
      description = "Classpath for resolving types of dependencies.")
  private String classpath = "";

  @Option(names = "--release", defaultValue = "25",
      description = "Java release passed to the compiler (default: ${DEFAULT-VALUE}).")
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

  @Option(names = "--output", paramLabel = "<file>",
      description = "Write output to this file instead of stdout.")
  private Path output;

  // The CLI is the one component allowed to write to stdout/stderr (ArchUnit-enforced);
  // this startup error precedes the output writer's creation.
  @Override
  @SuppressWarnings("PMD.SystemPrintln")
  public Integer call() {
    PrintWriter out;
    try {
      out = output != null
          ? new PrintWriter(java.nio.file.Files.newBufferedWriter(output,
              java.nio.charset.StandardCharsets.UTF_8))
          : new PrintWriter(System.out, true, java.nio.charset.StandardCharsets.UTF_8);
    } catch (java.io.IOException e) {
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
    rules = RuleRegistry.filterByPacks(RuleRegistry.filterByIds(rules, ruleIds), packs);
    if (rules.isEmpty()) {
      out.println("No rules selected.");
      return 2;
    }
    CompilationDriver driver = new CompilationDriver(release, classpath);
    AnalysisResult result = driver.analyzePaths(paths, rules);

    Reporter reporter = switch (format) {
      case "json" -> new JsonReporter();
      case "sarif" -> new SarifReporter();
      default -> new ConsoleReporter();
    };
    reporter.report(result.findings(), out);
    for (Map.Entry<Path, String> skip : result.skippedFiles().entrySet()) {
      out.println("skipped (does not compile): " + skip.getKey() + " — " + skip.getValue());
    }
    result.ruleFailures().forEach(f -> out.println("internal rule failure: " + f));
    return exitCode(result);
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
    java.util.Map<io.codekoll.api.RulePack, java.util.List<Rule>> byPack =
        new java.util.EnumMap<>(io.codekoll.api.RulePack.class);
    for (Rule rule : rules) {
      byPack.computeIfAbsent(rule.pack(), k -> new java.util.ArrayList<>()).add(rule);
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
