package io.codekoll.cli;

import io.codekoll.api.Rule;
import io.codekoll.api.Severity;
import io.codekoll.engine.AnalysisResult;
import io.codekoll.engine.CompilationDriver;
import io.codekoll.engine.RuleRegistry;
import io.codekoll.report.ConsoleReporter;
import io.codekoll.report.JsonReporter;
import io.codekoll.report.Reporter;
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
      description = "Output format: console, json (default: ${DEFAULT-VALUE}).")
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

  @Override
  public Integer call() {
    PrintWriter out = new PrintWriter(System.out, true, java.nio.charset.StandardCharsets.UTF_8);
    List<Rule> rules = RuleRegistry.loadAll();
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

    Reporter reporter = "json".equals(format) ? new JsonReporter() : new ConsoleReporter();
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
