package io.codekoll.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.codekoll.api.Finding;
import io.codekoll.api.Rule;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
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

  /** SPEC section 7: the rules array carries the metadata, and the pack as a tag. */
  @Test
  void sarifRuleDescriptorCarriesMetadataAndPackTag() {
    String out = render(new SarifReporter(PathRenderer.absolute(), List.of(new SampleRule())),
        List.of(SAMPLE));
    assertTrue(out.contains("\"shortDescription\": {\"text\": \"Reference equality on Strings\"}"), out);
    assertTrue(out.contains("\"fullDescription\""), out);
    assertTrue(out.contains("\"help\""), out);
    assertTrue(out.contains("\"tags\": [\"correctness\"]"), out);
  }

  /** Without metadata the descriptor keeps its previous shape: id and default level only. */
  @Test
  void sarifWithoutRuleMetadataStaysMinimal() {
    String out = render(new SarifReporter(), List.of(SAMPLE));
    assertTrue(out.contains("{\"id\": \"CK-REF-EQUALITY\", \"defaultConfiguration\""), out);
    assertFalse(out.contains("tags"), out);
  }

  private static final class SampleRule implements Rule {
    @Override
    public RuleId id() {
      return new RuleId("CK-REF-EQUALITY");
    }

    @Override
    public RulePack pack() {
      return RulePack.CORRECTNESS;
    }

    @Override
    public Severity defaultSeverity() {
      return Severity.ERROR;
    }

    @Override
    public String description() {
      return "Reference equality on Strings";
    }

    @Override
    public String explanation() {
      return "== compares references; two equal strings can be different objects.";
    }

    @Override
    public String fix() {
      return "Use equals().";
    }

    @Override
    public void scan(com.sun.source.tree.CompilationUnitTree unit, com.sun.source.util.Trees trees,
        javax.lang.model.util.Types types, javax.lang.model.util.Elements elements,
        io.codekoll.api.FindingCollector out) {
      throw new UnsupportedOperationException("not scanned in reporter tests");
    }
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
