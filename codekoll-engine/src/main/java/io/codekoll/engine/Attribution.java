package io.codekoll.engine;

import java.util.List;

/**
 * How much of the analyzed source javac was actually able to type-check.
 *
 * <p>This exists because the alternative is dishonest. A rule that needs resolved types cannot
 * fire on a file javac failed to attribute — usually because a dependency was missing — and
 * without this counter that outcome is indistinguishable from a clean run. "No findings" and
 * "no analysis" must never look the same.
 *
 * @param discovered files handed to the compiler
 * @param attributed files that type-checked, and on which type-aware rules could run
 * @param unresolved the distinct packages and symbols javac could not resolve, most common first
 */
public record Attribution(int discovered, int attributed, List<String> unresolved) {

  public Attribution {
    unresolved = List.copyOf(unresolved);
  }

  /** An empty tally, for runs that analyzed nothing. */
  public static Attribution empty() {
    return new Attribution(0, 0, List.of());
  }

  /** Files that failed to type-check. */
  public int failed() {
    return discovered - attributed;
  }

  /** Percentage of files that type-checked; 100 when nothing was analyzed. */
  public int coveragePercent() {
    return discovered == 0 ? 100 : (int) Math.round(100.0 * attributed / discovered);
  }

  /** True when every discovered file type-checked. */
  public boolean complete() {
    return attributed >= discovered;
  }

  /** Combines two tallies, as when merging batches or units. */
  public Attribution plus(Attribution other) {
    List<String> merged = new java.util.ArrayList<>(unresolved);
    for (String symbol : other.unresolved) {
      if (!merged.contains(symbol)) {
        merged.add(symbol);
      }
    }
    return new Attribution(discovered + other.discovered, attributed + other.attributed, merged);
  }
}
