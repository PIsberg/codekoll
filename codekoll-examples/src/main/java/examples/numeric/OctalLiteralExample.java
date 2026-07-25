package examples.numeric;

/**
 * Example for rule {@code CK-OCTAL-LITERAL}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} writes the timeout as {@code 0100}, intending
 * one hundred.
 *
 * <p><b>What happens at runtime:</b> a leading zero makes the literal OCTAL — {@code 0100}
 * is 64, not 100. The timeout is silently 36% shorter than intended; the code reads exactly
 * as the decimal value it does not compute.
 *
 * <p><b>How to fix it:</b> drop the leading zero, as {@link #fixed()} does.
 */
public class OctalLiteralExample {

  public int buggy() {
    return 0100; // :: CK-OCTAL-LITERAL
  }

  public int fixed() {
    return 100;
  }
}
