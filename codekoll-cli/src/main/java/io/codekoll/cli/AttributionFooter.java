package io.codekoll.cli;

import io.codekoll.engine.Attribution;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The lines that end a run which could not type-check every file (CLI-SPEC §6).
 *
 * <p>A file javac cannot attribute gets no rule at all. Listing each one, as the CLI did before,
 * buried the fact under a wall of {@code skipped} lines and then printed a clean-looking total; on
 * a repository whose dependencies were missing that read as "no bugs" when it meant "not looked
 * at". The footer states the count, the percentage and what was missing, and is the last thing
 * printed so it is the part that is read.
 *
 * <p>Plain ASCII on purpose: the run's own stderr is often a Windows console in a legacy code
 * page, where the em dash of the old per-file line came out as a replacement character.
 */
final class AttributionFooter {

  /** How many unresolved names to list before saying how many more there are. */
  private static final int NAMES_SHOWN = 5;

  private AttributionFooter() {
  }

  /** The footer, or no lines at all when every file type-checked. */
  static List<String> lines(Attribution attribution) {
    if (attribution.complete()) {
      return List.of();
    }
    List<String> lines = new ArrayList<>();
    lines.add("warning: " + attribution.failed() + " of " + attribution.discovered()
        + " files could not be type-checked (" + attribution.coveragePercent()
        + "% type-checked), so no rule ran on them and a clean result does not cover them.");
    Map<String, Integer> unresolved = attribution.unresolved();
    if (!unresolved.isEmpty()) {
      lines.add("  Unresolved, by files affected: " + names(unresolved));
    }
    if (unresolved.keySet().stream().anyMatch(AttributionFooter::isLombok)) {
      lines.add("  Lombok is among them: codekoll runs javac with -proc:none, so members Lombok"
          + " generates never exist. Analyze delomboked sources (lombok delombok) to cover"
          + " those files.");
    }
    lines.add("  Put the missing jars on --classpath. --verbose lists every skipped file.");
    return List.copyOf(lines);
  }

  private static String names(Map<String, Integer> unresolved) {
    List<String> shown = unresolved.entrySet().stream()
        .limit(NAMES_SHOWN)
        .map(e -> e.getKey() + " (" + e.getValue() + (e.getValue() == 1 ? " file)" : " files)"))
        .toList();
    String text = String.join(", ", shown);
    int more = unresolved.size() - shown.size();
    return more > 0 ? text + ", and " + more + " more" : text;
  }

  private static boolean isLombok(String name) {
    return "lombok".equals(name) || name.startsWith("lombok.");
  }
}
