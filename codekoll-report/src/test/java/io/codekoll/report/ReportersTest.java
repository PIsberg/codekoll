package io.codekoll.report;

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
}
