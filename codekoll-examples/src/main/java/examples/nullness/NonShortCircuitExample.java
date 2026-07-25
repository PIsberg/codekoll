package examples.nullness;

/**
 * Example for rule {@code CK-NON-SHORT-CIRCUIT}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} guards with {@code &} instead of
 * {@code &&} — one keystroke short.
 *
 * <p><b>What happens at runtime:</b> single {@code &} evaluates BOTH operands
 * unconditionally. When {@code name} is null the "guarded" {@code name.isEmpty()} still
 * runs: guaranteed NullPointerException on precisely the input the guard was written for.
 *
 * <p><b>How to fix it:</b> the short-circuit operator, as {@link #fixed(String)} does.
 */
public class NonShortCircuitExample {

  public boolean buggy(String name) {
    return name != null & !name.isEmpty(); // :: CK-NON-SHORT-CIRCUIT
  }

  public boolean fixed(String name) {
    return name != null && !name.isEmpty();
  }
}
