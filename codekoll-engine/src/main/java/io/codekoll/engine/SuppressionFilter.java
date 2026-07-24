package io.codekoll.engine;

import com.sun.source.tree.CompilationUnitTree;
import io.codekoll.api.Finding;
import java.io.IOException;
import java.util.List;

/**
 * Line-comment suppression: a finding whose source line contains {@code codekoll:off}
 * (optionally followed by specific rule ids) is dropped.
 *
 * <p>{@code @SuppressWarnings("codekoll:CK-…")} element-level suppression is planned
 * (SPEC §3.4) and will be added alongside the combined dispatcher.
 */
final class SuppressionFilter {

  private static final String MARKER = "codekoll:off";

  private final List<String> lines;

  private SuppressionFilter(List<String> lines) {
    this.lines = lines;
  }

  static SuppressionFilter forUnit(CompilationUnitTree unit) {
    try {
      CharSequence content = unit.getSourceFile().getCharContent(true);
      return new SuppressionFilter(content.toString().lines().toList());
    } catch (IOException e) {
      return new SuppressionFilter(List.of());
    }
  }

  boolean isSuppressed(Finding finding) {
    int idx = (int) finding.line() - 1;
    if (idx < 0 || idx >= lines.size()) {
      return false;
    }
    String line = lines.get(idx);
    int marker = line.indexOf(MARKER);
    if (marker < 0) {
      return false;
    }
    String rest = line.substring(marker + MARKER.length()).trim();
    return rest.isEmpty() || rest.contains(finding.rule().value());
  }
}
