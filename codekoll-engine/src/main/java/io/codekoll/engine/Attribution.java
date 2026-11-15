package io.codekoll.engine;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How much of the analyzed source javac was actually able to type-check.
 *
 * <p>This exists because the alternative is dishonest. A file javac failed to attribute, usually
 * because a dependency was missing, gets no rule at all, and without this counter that outcome is
 * indistinguishable from a clean run. "No findings" and "no analysis" must never look the same.
 *
 * @param discovered files handed to the compiler
 * @param attributed files that type-checked, and on which rules ran
 * @param unresolved each package or class javac could not resolve, mapped to the number of files
 *     whose errors name it; ordered most files first, then by name, so output is deterministic
 */
public record Attribution(int discovered, int attributed, Map<String, Integer> unresolved) {

  public Attribution {
    Map<String, Integer> ordered = new LinkedHashMap<>();
    unresolved.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
            .thenComparing(Map.Entry.comparingByKey()))
        .forEach(e -> ordered.put(e.getKey(), e.getValue()));
    unresolved = Collections.unmodifiableMap(ordered);
  }

  /** An empty tally, for runs that analyzed nothing. */
  public static Attribution empty() {
    return new Attribution(0, 0, Map.of());
  }

  /** Files that failed to type-check. */
  public int failed() {
    return discovered - attributed;
  }

  /**
   * Percentage of files that type-checked, rounded down; 100 when nothing was analyzed.
   *
   * <p>Rounded down on purpose: 762 of 765 rounds to 100, and a footer reading "100 %" over three
   * unchecked files is the confusion this class exists to prevent.
   */
  public int coveragePercent() {
    return discovered == 0 ? 100 : (int) (100L * attributed / discovered);
  }

  /** True when every discovered file type-checked. */
  public boolean complete() {
    return attributed >= discovered;
  }

  /** Combines two tallies, as when merging units: counts add, and the order is recomputed. */
  public Attribution plus(Attribution other) {
    Map<String, Integer> merged = new HashMap<>(unresolved);
    other.unresolved.forEach((name, files) -> merged.merge(name, files, Integer::sum));
    return new Attribution(discovered + other.discovered, attributed + other.attributed, merged);
  }
}
