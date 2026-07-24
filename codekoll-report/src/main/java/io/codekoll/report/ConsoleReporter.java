package io.codekoll.report;

import io.codekoll.api.Finding;
import io.codekoll.api.Severity;
import java.io.PrintWriter;
import java.util.List;

/** Human-oriented console output: location, severity, rule id, snippet, message. */
public final class ConsoleReporter implements Reporter {

  @Override
  public void report(List<Finding> findings, PrintWriter out) {
    for (Finding f : findings) {
      out.printf("%s:%d:%d  %-7s %s%n", f.file(), f.line(), f.column(),
          f.severity(), f.rule());
      if (!f.snippet().isEmpty()) {
        out.printf("    %s%n", f.snippet());
      }
      out.printf("  %s%n%n", f.message());
    }
    long errors = count(findings, Severity.ERROR);
    long warnings = count(findings, Severity.WARNING);
    long infos = count(findings, Severity.INFO);
    if (findings.isEmpty()) {
      out.println("No findings.");
    } else {
      out.printf("%d error(s), %d warning(s), %d info%n", errors, warnings, infos);
    }
    out.flush();
  }

  private static long count(List<Finding> findings, Severity severity) {
    return findings.stream().filter(f -> f.severity() == severity).count();
  }
}
