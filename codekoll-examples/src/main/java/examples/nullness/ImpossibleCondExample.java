package examples.nullness;

/**
 * Example for rule {@code CK-IMPOSSIBLE-COND}.
 *
 * <p><b>What is wrong:</b> the condition in {@link #buggy(String)} requires {@code id} to be
 * null on the left of {@code &&} and then dereferences it on the right. Both can never hold
 * at once — a mistyped operator (the author meant {@code !=}).
 *
 * <p><b>What happens at runtime:</b> the branch is dead code — the "valid" path never
 * executes for any input, and if the left side ever passed, the right side would throw a
 * {@code NullPointerException}. Validation silently never happens.
 *
 * <p><b>How to fix it:</b> guard first, then use: {@code id != null && id.length() > 5}, as
 * {@link #fixed(String)} does.
 */
public class ImpossibleCondExample {

  public boolean buggy(String id) {
    if (id == null && id.length() > 5) { // :: CK-IMPOSSIBLE-COND
      return true;
    }
    return false;
  }

  public boolean fixed(String id) {
    return id != null && id.length() > 5;
  }
}
