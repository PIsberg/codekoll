package examples.numeric;

/**
 * Example for rule {@code CK-DIV-ZERO}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(int)} divides by a hardcoded integer zero —
 * probably a placeholder that was never replaced.
 *
 * <p><b>What happens at runtime:</b> {@code ArithmeticException: / by zero} on every single
 * execution. It compiled only because the dividend is not a compile-time constant.
 *
 * <p><b>How to fix it:</b> divide by the intended variable and guard the zero case, as
 * {@link #fixed(int, int)} does.
 */
public class DivZeroExample {

  public int buggy(int total) {
    return total / 0; // :: CK-DIV-ZERO
  }

  public int fixed(int total, int count) {
    return count == 0 ? 0 : total / count;
  }
}
