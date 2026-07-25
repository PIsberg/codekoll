package examples.correctness;

/**
 * Example for rule {@code CK-NAN-COMPARE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(double)} guards against NaN with
 * {@code result == Double.NaN}.
 *
 * <p><b>What happens at runtime:</b> IEEE 754 defines NaN as unequal to everything —
 * including itself. {@code x == Double.NaN} is <em>always false</em>, so the guard never
 * fires and NaN values sail straight through into downstream arithmetic, poisoning every
 * result they touch.
 *
 * <p><b>How to fix it:</b> use {@code Double.isNaN(x)}, as {@link #fixed(double)} does.
 */
public class NanCompareExample {

  public double buggy(double result) {
    if (result == Double.NaN) { // :: CK-NAN-COMPARE
      return 0.0;
    }
    return result;
  }

  public double fixed(double result) {
    if (Double.isNaN(result)) {
      return 0.0;
    }
    return result;
  }
}
