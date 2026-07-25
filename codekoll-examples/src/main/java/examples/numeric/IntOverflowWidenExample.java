package examples.numeric;

/**
 * Example for rule {@code CK-INT-OVERFLOW-WIDEN}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(int)} computes a millisecond duration as
 * {@code days * 86_400_000} where both operands are {@code int}.
 *
 * <p><b>What happens at runtime:</b> the multiplication is done in 32-bit int and only then
 * widened to long. Past ~24 days it overflows and wraps — often to a NEGATIVE number — and
 * the widening faithfully preserves the wrong value. A 30-day retention window becomes a
 * negative duration.
 *
 * <p><b>How to fix it:</b> make one operand {@code long} so the whole expression is 64-bit,
 * as {@link #fixed(int)} does.
 */
public class IntOverflowWidenExample {

  public long buggy(int days) {
    return days * 86_400_000; // :: CK-INT-OVERFLOW-WIDEN
  }

  public long fixed(int days) {
    return days * 86_400_000L;
  }
}
