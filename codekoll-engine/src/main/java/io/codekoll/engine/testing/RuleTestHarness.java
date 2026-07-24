package io.codekoll.engine.testing;

import io.codekoll.api.Finding;
import io.codekoll.api.Rule;
import io.codekoll.engine.AnalysisResult;
import io.codekoll.engine.CompilationDriver;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Fixture harness: compiles a source string in-memory, runs a single rule, and compares the
 * findings against {@code // :: finding-here} (or {@code // :: CK-…}) line markers.
 *
 * <p>Positive fixtures must produce findings at exactly the marked lines — no more, no fewer.
 * Negative fixtures (no markers) must produce zero findings.
 */
public final class RuleTestHarness {

  private static final String MARKER = "// ::";

  private RuleTestHarness() {}

  /** Runs {@code rule} over the given source; throws AssertionError on marker mismatch. */
  public static void assertFixture(Rule rule, String className, String source) {
    AnalysisResult result = run(rule, className, source);
    if (!result.skippedFiles().isEmpty()) {
      throw new AssertionError("Fixture failed to compile: " + result.skippedFiles());
    }
    if (!result.ruleFailures().isEmpty()) {
      throw new AssertionError("Rule crashed: " + result.ruleFailures());
    }
    Set<Long> expected = markedLines(source);
    Set<Long> actual = result.findings().stream()
        .map(Finding::line)
        .collect(Collectors.toCollection(TreeSet::new));
    if (!expected.equals(actual)) {
      throw new AssertionError(rule.id() + " findings mismatch.\n  expected lines: " + expected
          + "\n  actual lines:   " + actual + "\n  findings: " + result.findings());
    }
  }

  /** Runs the rule and returns the raw result (for tests asserting messages etc.). */
  public static AnalysisResult run(Rule rule, String className, String source) {
    CompilationDriver driver = new CompilationDriver(25, "");
    return driver.analyzeFileObjects(List.of(new StringSource(className, source)), List.of(rule));
  }

  private static Set<Long> markedLines(String source) {
    Set<Long> lines = new TreeSet<>();
    List<String> all = new ArrayList<>(source.lines().toList());
    for (int i = 0; i < all.size(); i++) {
      if (all.get(i).contains(MARKER)) {
        lines.add((long) (i + 1));
      }
    }
    return lines;
  }
}
