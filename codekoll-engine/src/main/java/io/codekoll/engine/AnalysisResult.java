package io.codekoll.engine;

import io.codekoll.api.Finding;
import io.codekoll.api.Severity;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outcome of one analysis run.
 *
 * @param findings all findings, in file/line order
 * @param skippedFiles files that failed attribution, mapped to the first compiler error
 * @param ruleFailures internal rule crashes (rule id + message); analysis continued past them
 */
public record AnalysisResult(
    List<Finding> findings,
    Map<Path, String> skippedFiles,
    List<String> ruleFailures) {

  public AnalysisResult {
    // Defensive copies: record components must not alias caller-mutable collections
    // (codekoll's own CK-RECORD-MUTABLE-COMPONENT rule, and SpotBugs EI_EXPOSE_REP).
    findings = List.copyOf(findings);
    skippedFiles = Collections.unmodifiableMap(new LinkedHashMap<>(skippedFiles));
    ruleFailures = List.copyOf(ruleFailures);
  }

  public boolean hasErrors() {
    return findings.stream().anyMatch(f -> f.severity() == Severity.ERROR);
  }
}
