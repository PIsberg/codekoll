package io.codekoll.report;

import io.codekoll.api.Finding;
import io.codekoll.api.Rule;
import io.codekoll.api.Severity;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SARIF 2.1.0 output for GitHub code scanning, the VS Code SARIF viewer, and CI annotators.
 * Emits one {@code rules} descriptor per distinct rule seen and one {@code result} per
 * finding with a physical location.
 */
public final class SarifReporter implements Reporter {

  private static final String SCHEMA =
      "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/"
          + "sarif-schema-2.1.0.json";

  private final PathRenderer paths;
  private final Map<String, Rule> rules;

  /** Reports absolute paths, with no rule metadata. */
  public SarifReporter() {
    this(PathRenderer.absolute());
  }

  /**
   * @param paths how file paths are rendered; repo-relative in a normal CLI run, which is what
   *     GitHub code scanning needs to annotate a pull request
   */
  public SarifReporter(PathRenderer paths) {
    this(paths, List.of());
  }

  /**
   * @param paths how file paths are rendered
   * @param rules the loaded rules, whose metadata fills each descriptor (SPEC section 7):
   *     {@code description()} to {@code shortDescription}, {@code explanation()} to
   *     {@code fullDescription}, {@code fix()} to {@code help}, and the pack as the only tag,
   *     so code scanning can filter by pack. An unknown rule id still gets a minimal descriptor.
   */
  public SarifReporter(PathRenderer paths, List<Rule> rules) {
    this.paths = paths;
    Map<String, Rule> byId = new LinkedHashMap<>();
    rules.forEach(rule -> byId.put(rule.id().value(), rule));
    this.rules = Map.copyOf(byId);
  }

  @Override
  public void report(List<Finding> findings, PrintWriter out) {
    Map<String, Finding> ruleExemplar = new LinkedHashMap<>();
    for (Finding f : findings) {
      ruleExemplar.putIfAbsent(f.rule().value(), f);
    }

    out.println("{");
    out.println("  \"$schema\": \"" + SCHEMA + "\",");
    out.println("  \"version\": \"2.1.0\",");
    out.println("  \"runs\": [{");
    out.println("    \"tool\": {\"driver\": {");
    out.println("      \"name\": \"codekoll\",");
    out.println("      \"informationUri\": \"https://github.com/codekoll/codekoll\",");
    out.println("      \"rules\": [");
    List<Finding> exemplars = List.copyOf(ruleExemplar.values());
    for (int i = 0; i < exemplars.size(); i++) {
      Finding f = exemplars.get(i);
      String comma = i < exemplars.size() - 1 ? "," : "";
      Rule rule = rules.get(f.rule().value());
      if (rule == null) {
        out.printf("        {\"id\": \"%s\", \"defaultConfiguration\": "
                + "{\"level\": \"%s\"}}%s%n",
            f.rule().value(), sarifLevel(f.severity()), comma);
      } else {
        out.printf("        {\"id\": \"%s\", \"defaultConfiguration\": {\"level\": \"%s\"}, "
                + "\"shortDescription\": {\"text\": \"%s\"}, "
                + "\"fullDescription\": {\"text\": \"%s\"}, "
                + "\"help\": {\"text\": \"%s\"}, "
                + "\"properties\": {\"tags\": [\"%s\"]}}%s%n",
            f.rule().value(), sarifLevel(f.severity()), escape(rule.description()),
            escape(rule.explanation()), escape(rule.fix()), escape(rule.pack().id()), comma);
      }
    }
    out.println("      ]");
    out.println("    }},");
    out.println("    \"results\": [");
    for (int i = 0; i < findings.size(); i++) {
      Finding f = findings.get(i);
      out.printf("      {%n");
      out.printf("        \"ruleId\": \"%s\",%n", f.rule().value());
      out.printf("        \"level\": \"%s\",%n", sarifLevel(f.severity()));
      out.printf("        \"message\": {\"text\": \"%s\"},%n", escape(f.message()));
      out.printf("        \"locations\": [{\"physicalLocation\": {%n");
      out.printf("          \"artifactLocation\": {\"uri\": \"%s\"},%n",
          escape(uri(f)));
      out.printf("          \"region\": {\"startLine\": %d, \"startColumn\": %d}%n",
          Math.max(1, f.line()), Math.max(1, f.column()));
      out.printf("        }}]%n");
      out.printf("      }%s%n", i < findings.size() - 1 ? "," : "");
    }
    out.println("    ]");
    out.println("  }]");
    out.println("}");
    out.flush();
  }

  private static String sarifLevel(Severity severity) {
    return switch (severity) {
      case ERROR -> "error";
      case WARNING -> "warning";
      case INFO -> "note";
    };
  }

  /** SARIF URIs are always {@code /}-separated, whatever the OS the run happened on. */
  private String uri(Finding f) {
    return paths.render(f.file()).replace('\\', '/');
  }

  private static String escape(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }
}
