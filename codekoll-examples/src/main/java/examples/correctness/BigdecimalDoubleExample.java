package examples.correctness;

import java.math.BigDecimal;

/**
 * Example for rule {@code CK-BIGDECIMAL-DOUBLE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} builds an exact-arithmetic BigDecimal from the
 * double literal {@code 0.1}.
 *
 * <p><b>What happens at runtime:</b> the double 0.1 is not exactly 0.1, and the constructor
 * faithfully preserves the binary approximation:
 * {@code 0.1000000000000000055511151231257827021181583404541015625}. Money math built on it
 * drifts by fractions of a cent — exactly the drift BigDecimal was chosen to prevent.
 *
 * <p><b>How to fix it:</b> {@code BigDecimal.valueOf} or the String constructor, as
 * {@link #fixed()} does.
 */
public class BigdecimalDoubleExample {

  public BigDecimal buggy() {
    BigDecimal vatRate = new BigDecimal(0.1); // :: CK-BIGDECIMAL-DOUBLE
    return new BigDecimal("100").multiply(vatRate);
  }

  public BigDecimal fixed() {
    BigDecimal vatRate = new BigDecimal("0.1");
    return new BigDecimal("100").multiply(vatRate);
  }
}
