package examples.resources;

/**
 * Example for rule {@code CK-THROW-IN-FINALLY}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} returns a fallback value from inside
 * {@code finally}.
 *
 * <p><b>What happens at runtime:</b> the finally block runs while the try body's exception
 * is propagating — and its {@code return} REPLACES that exception. The method "succeeds"
 * with the fallback even when parsing failed, and the real error (the one explaining what
 * went wrong) vanishes without a trace.
 *
 * <p><b>How to fix it:</b> keep finally for cleanup only; handle fallbacks in a catch
 * block, as {@link #fixed()} does.
 */
public class ThrowInFinallyExample {

  public int buggy() {
    try {
      return parse();
    } finally {
      return -1; // :: CK-THROW-IN-FINALLY
    }
  }

  public int fixed() {
    try {
      return parse();
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private int parse() {
    return Integer.parseInt("not-a-number");
  }
}
