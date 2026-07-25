package examples.correctness;

import java.math.BigDecimal;

/**
 * Example for rule {@code CK-BIGDECIMAL-EQUALS}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(BigDecimal, BigDecimal)} compares two amounts with
 * {@code equals}.
 *
 * <p><b>What happens at runtime:</b> BigDecimal.equals compares scale as well as value:
 * {@code new BigDecimal("1.0").equals(new BigDecimal("1.00"))} is {@code false}. Two amounts
 * that are numerically equal but parsed with different precision fail the check — the
 * reconciliation that should balance reports a phantom discrepancy of zero cents.
 *
 * <p><b>How to fix it:</b> {@code compareTo(...) == 0} for value equality, as
 * {@link #fixed(BigDecimal, BigDecimal)} does.
 */
public class BigdecimalEqualsExample {

  public boolean buggy(BigDecimal expected, BigDecimal actual) {
    return expected.equals(actual); // :: CK-BIGDECIMAL-EQUALS
  }

  public boolean fixed(BigDecimal expected, BigDecimal actual) {
    return expected.compareTo(actual) == 0;
  }
}
