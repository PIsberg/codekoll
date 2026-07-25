package examples.correctness;

/**
 * Example for rule {@code CK-EQUALS-NULL-ARG}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} tests for null with
 * {@code s.equals(null)}.
 *
 * <p><b>What happens at runtime:</b> the equals() contract guarantees the result is
 * {@code false} for a null argument — always. Worse, when {@code s} itself is null (the case
 * being tested!), the call throws a NullPointerException before "returning" anything.
 * The null check can never succeed.
 *
 * <p><b>How to fix it:</b> use {@code == null}, as {@link #fixed(String)} does.
 */
public class EqualsNullArgExample {

  public boolean buggy(String s) {
    return s.equals(null); // :: CK-EQUALS-NULL-ARG
  }

  public boolean fixed(String s) {
    return s == null;
  }
}
