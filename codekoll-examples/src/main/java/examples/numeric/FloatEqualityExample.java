package examples.numeric;

/**
 * Example for rule {@code CK-FLOAT-EQUALITY}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(double, double)} compares two computed doubles
 * with exact {@code ==}.
 *
 * <p><b>What happens at runtime:</b> floating-point arithmetic rounds — famously,
 * {@code 0.1 + 0.2 != 0.3}. Mathematically equal computations differ in the last bits, so
 * the equality check fails for specific input values and the reconciliation reports a
 * mismatch that "cannot happen".
 *
 * <p><b>How to fix it:</b> compare with a tolerance, as {@link #fixed(double, double)}
 * does.
 */
public class FloatEqualityExample {

  public boolean buggy(double computedTotal, double expectedTotal) {
    return computedTotal == expectedTotal; // :: CK-FLOAT-EQUALITY
  }

  public boolean fixed(double computedTotal, double expectedTotal) {
    return Math.abs(computedTotal - expectedTotal) < 1e-9;
  }
}
