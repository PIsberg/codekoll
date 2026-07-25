package examples.numeric;

import java.util.Comparator;

/**
 * Example for rule {@code CK-COMPARE-SUBTRACT}.
 *
 * <p><b>What is wrong:</b> the comparator returned by {@link #buggy()} implements
 * {@code compare} as {@code a - b} — the classic shortcut.
 *
 * <p><b>What happens at runtime:</b> subtraction overflows for operands far apart: a
 * large-negative minus a large-positive wraps to a POSITIVE result, so the comparator
 * reports the wrong order. Sorting misplaces elements or throws "Comparison method violates
 * its general contract!" — but only on production data with extreme values.
 *
 * <p><b>How to fix it:</b> {@code Integer.compare(a, b)}, as {@link #fixed()} does.
 */
public class CompareSubtractExample {

  static class SubtractingComparator implements Comparator<Integer> {
    @Override
    public int compare(Integer a, Integer b) {
      return a - b; // :: CK-COMPARE-SUBTRACT
    }
  }

  public Comparator<Integer> buggy() {
    return new SubtractingComparator();
  }

  public Comparator<Integer> fixed() {
    return Comparator.comparingInt(Integer::intValue);
  }
}
