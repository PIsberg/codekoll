package io.codekoll.report;

import io.codekoll.api.Finding;
import java.io.PrintWriter;
import java.util.List;

/** Stable flat-JSON output for custom tooling (SARIF is the CI-oriented format). */
public final class JsonReporter implements Reporter {

  @Override
  public void report(List<Finding> findings, PrintWriter out) {
    out.println("[");
    for (int i = 0; i < findings.size(); i++) {
      Finding f = findings.get(i);
      out.printf("  {\"rule\":\"%s\",\"severity\":\"%s\",\"file\":\"%s\",\"line\":%d,"
              + "\"column\":%d,\"message\":\"%s\",\"snippet\":\"%s\"}%s%n",
          f.rule(), f.severity(), escape(f.file().toString()), f.line(), f.column(),
          escape(f.message()), escape(f.snippet()), i < findings.size() - 1 ? "," : "");
    }
    out.println("]");
    out.flush();
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
