package io.codekoll.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.codekoll.api.Finding;
import io.codekoll.api.RuleId;
import io.codekoll.api.Severity;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportersTest {

  private static final Finding SAMPLE = new Finding(
      new RuleId("CK-REF-EQUALITY"), Severity.ERROR, Path.of("src/Foo.java"),
      42, 16, "== compares references, not contents.", "if (a == b) {");

  private String render(Reporter reporter, List<Finding> findings) {
    StringWriter sw = new StringWriter();
    reporter.report(findings, new PrintWriter(sw));
    return sw.toString();
  }

  @Test
  void consoleShowsLocationRuleAndCounts() {
    String out = render(new ConsoleReporter(), List.of(SAMPLE));
    assertTrue(out.contains("CK-REF-EQUALITY"), "rule id");
    assertTrue(out.contains("42:16"), "location");
    assertTrue(out.contains("1 error(s)"), "summary");
  }

  @Test
  void consoleReportsCleanWhenEmpty() {
    assertTrue(render(new ConsoleReporter(), List.of()).contains("No findings"));
  }

  @Test
  void jsonIsWellFormedArray() {
    String out = render(new JsonReporter(), List.of(SAMPLE)).trim();
    assertTrue(out.startsWith("["), "starts with array");
    assertTrue(out.endsWith("]"), "ends with array");
    assertTrue(out.contains("\"rule\":\"CK-REF-EQUALITY\""), "rule field");
    assertTrue(out.contains("\"line\":42"), "line field");
  }

  @Test
  void sarifHasSchemaRuleDescriptorAndResult() {
    String out = render(new SarifReporter(), List.of(SAMPLE));
    assertTrue(out.contains("sarif-schema-2.1.0"), "schema");
    assertTrue(out.contains("\"version\": \"2.1.0\""), "version");
    assertTrue(out.contains("\"ruleId\": \"CK-REF-EQUALITY\""), "result ruleId");
    assertTrue(out.contains("\"level\": \"error\""), "sarif level mapping");
    assertTrue(out.contains("\"startLine\": 42"), "region");
    assertTrue(out.contains("src/Foo.java"), "artifact uri");
  }

  // ------------------------------------------------- path rendering (CLI-SPEC §7.1)

  private static final Path REPO_ROOT = Path.of(System.getProperty("user.dir")).resolve("repo");

  private static final Path ABSOLUTE_FILE = REPO_ROOT.resolve("src/main/java/Foo.java");

  private static final Finding ABSOLUTE_FINDING = new Finding(
      new RuleId("CK-REF-EQUALITY"), Severity.ERROR, ABSOLUTE_FILE,
      42, 16, "== compares references, not contents.", "if (a == b) {");

  /** Stands in for the workspace's relativizer without dragging that module in. */
  private static PathRenderer under(Path root) {
    return file -> root.relativize(file).toString().replace('\\', '/');
  }

  @Test
  void consoleRendersThroughTheGivenRenderer() {
    String out = render(new ConsoleReporter(under(REPO_ROOT)), List.of(ABSOLUTE_FINDING));

    assertTrue(out.contains("src/main/java/Foo.java"), "repo-relative path");
    assertFalse(out.contains(REPO_ROOT.toString()), "no absolute prefix");
  }

  @Test
  void jsonRendersThroughTheGivenRenderer() {
    String out = render(new JsonReporter(under(REPO_ROOT)), List.of(ABSOLUTE_FINDING));

    assertTrue(out.contains("\"file\":\"src/main/java/Foo.java\""), "repo-relative path");
  }

  /**
   * SARIF URIs decide whether GitHub can annotate a pull request: an absolute path from a build
   * agent annotates nothing, and a Windows separator is not a URI.
   */
  @Test
  void sarifUriIsRelativeAndForwardSlashed() {
    String out = render(new SarifReporter(under(REPO_ROOT)), List.of(ABSOLUTE_FINDING));

    assertTrue(out.contains("\"uri\": \"src/main/java/Foo.java\""), "repo-relative uri");
    assertFalse(out.contains("\\\\"), "no escaped backslashes in the uri");
  }

  @Test
  void defaultRendererStillPrintsAbsolutePaths() {
    String out = render(new ConsoleReporter(), List.of(ABSOLUTE_FINDING));

    assertTrue(out.contains(ABSOLUTE_FILE.toString()), "--absolute-paths behaviour");
  }
}
