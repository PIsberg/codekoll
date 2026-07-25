package examples.numeric;

/**
 * Example for rule {@code CK-INT-DIV-FLOAT}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(int, int)} computes a hit rate with
 * {@code double ratio = hits / total;} — both operands are {@code int}.
 *
 * <p><b>What happens at runtime:</b> the division truncates FIRST (integer division) and
 * only then widens to double: 45 hits of 100 total gives {@code 0.0}, not {@code 0.45}.
 * The dashboard shows a permanent 0% hit rate and nobody knows why.
 *
 * <p><b>How to fix it:</b> widen an operand before dividing, as {@link #fixed(int, int)}
 * does.
 */
public class IntDivFloatExample {

  public double buggy(int hits, int total) {
    double ratio = hits / total; // :: CK-INT-DIV-FLOAT
    return ratio * 100;
  }

  public double fixed(int hits, int total) {
    double ratio = (double) hits / total;
    return ratio * 100;
  }
}
