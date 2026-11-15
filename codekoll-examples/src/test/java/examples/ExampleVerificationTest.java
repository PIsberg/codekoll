package examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.codekoll.api.Finding;
import io.codekoll.api.Rule;
import io.codekoll.engine.AnalysisResult;
import io.codekoll.engine.CompilationDriver;
import io.codekoll.engine.RuleRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End-to-end verification over the examples module itself:
 * every registered rule fires at exactly its {@code // :: CK-…} marked lines,
 * fixed() variants stay silent, every rule has an example class, and every example
 * documents what is wrong / what happens at runtime / how to fix it.
 */
class ExampleVerificationTest {

  private static final Path SOURCES = Path.of("src", "main", "java");

  /**
   * Examples for rules that only apply to projects targeting an older release
   * (CK-VT-SYNC-PINNING: JDK 21-23), analyzed at {@code --release 21}. Maven does not compile
   * this root; the analyzer does.
   */
  private static final Path RELEASE_21 = Path.of("src", "release21", "java");

  private static List<Rule> rules;
  private static List<Finding> findings;

  @BeforeAll
  static void analyzeExamples() {
    rules = RuleRegistry.loadAll();
    AnalysisResult current = analyze(SOURCES, 25);
    AnalysisResult release21 = analyze(RELEASE_21, 21);
    findings = Stream.concat(current.findings().stream(), release21.findings().stream()).toList();
  }

  private static AnalysisResult analyze(Path root, int release) {
    AnalysisResult result = new CompilationDriver(release, "").analyzePaths(List.of(root), rules);
    assertTrue(result.skippedFiles().isEmpty(),
        "Examples must compile (they are runtime bugs): " + result.skippedFiles());
    assertTrue(result.ruleFailures().isEmpty(), "Rule crashes: " + result.ruleFailures());
    return result;
  }

  @Test
  void findingsMatchMarkersExactly() throws IOException {
    Set<String> expected = new TreeSet<>();
    for (Path file : exampleFiles()) {
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        int marker = line.indexOf("// ::");
        if (marker >= 0) {
          String id = line.substring(marker + 5).trim().split("\\s+")[0];
          expected.add(file.getFileName() + ":" + (i + 1) + ":" + id);
        }
      }
    }
    Set<String> actual = findings.stream()
        .map(f -> f.file().getFileName() + ":" + f.line() + ":" + f.rule())
        .collect(Collectors.toCollection(TreeSet::new));
    assertEquals(expected, actual,
        "Findings must match example markers exactly (fixed() variants must stay silent)");
  }

  @Test
  void everyRuleHasAnExampleWithFindings() {
    Set<String> firedRules = findings.stream()
        .map(f -> f.rule().value())
        .collect(Collectors.toSet());
    for (Rule rule : rules) {
      assertTrue(firedRules.contains(rule.id().value()),
          rule.id() + " has no firing example in codekoll-examples — a rule cannot ship "
              + "without a documented example (PLAN M8)");
    }
  }

  @Test
  void everyRuleHasAnExampleClassByNamingConvention() throws IOException {
    // Case-insensitive: CK-STATIC-DATEFORMAT ↔ StaticDateFormatExample both allowed.
    Set<String> exampleClassNames = exampleFiles().stream()
        .map(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
    for (Rule rule : rules) {
      String expectedName = exampleClassName(rule.id().value());
      assertTrue(exampleClassNames.contains(expectedName.toLowerCase(Locale.ROOT)),
          rule.id() + " needs example class " + expectedName);
    }
  }

  @Test
  void everyExampleDocumentsWrongRuntimeAndFix() throws IOException {
    for (Path file : exampleFiles()) {
      String content = Files.readString(file, StandardCharsets.UTF_8);
      for (String section : List.of("What is wrong", "What happens at runtime", "How to fix")) {
        assertTrue(content.contains(section),
            file.getFileName() + " Javadoc must contain the section '" + section + "'");
      }
      assertTrue(content.contains("fixed("),
          file.getFileName() + " must contain a fixed() variant that stays silent");
    }
  }

  private static List<Path> exampleFiles() throws IOException {
    List<Path> files = new ArrayList<>();
    for (Path root : List.of(SOURCES, RELEASE_21)) {
      try (Stream<Path> walk = Files.walk(root)) {
        files.addAll(walk.filter(p -> p.toString().endsWith("Example.java")).toList());
      }
    }
    return files;
  }

  /** CK-EMPTY-CATCH → EmptyCatchExample.java */
  private static String exampleClassName(String ruleId) {
    StringBuilder sb = new StringBuilder();
    for (String part : ruleId.substring("CK-".length()).split("-")) {
      sb.append(part.charAt(0)).append(part.substring(1).toLowerCase(Locale.ROOT));
    }
    return sb.append("Example.java").toString();
  }

  @Test
  void severitiesComeFromRuleDefaults() {
    for (Finding f : findings) {
      Rule rule = rules.stream()
          .filter(r -> r.id().equals(f.rule()))
          .findFirst()
          .orElseThrow();
      assertEquals(rule.defaultSeverity(), f.severity());
    }
  }
}
