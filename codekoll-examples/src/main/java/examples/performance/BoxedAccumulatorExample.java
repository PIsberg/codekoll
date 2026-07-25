package examples.performance;

import java.util.List;

/**
 * Example for rule {@code CK-BOXED-ACCUMULATOR}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(List)} sums into a boxed {@code Long}
 * accumulator inside the loop.
 *
 * <p><b>What happens at runtime:</b> {@code total += v} on a boxed Long is
 * unbox-add-REBOX: a fresh Long object per iteration once values leave the −128..127
 * cache. Summing a million elements allocates a million temporaries to produce one number
 * — visible as GC pressure, invisible in the code.
 *
 * <p><b>How to fix it:</b> accumulate in a primitive, as {@link #fixed(List)} does.
 */
public class BoxedAccumulatorExample {

  public Long buggy(List<Integer> amounts) {
    Long total = 0L;
    for (int amount : amounts) {
      total += amount; // :: CK-BOXED-ACCUMULATOR
    }
    return total;
  }

  public long fixed(List<Integer> amounts) {
    long total = 0L;
    for (int amount : amounts) {
      total += amount;
    }
    return total;
  }
}
