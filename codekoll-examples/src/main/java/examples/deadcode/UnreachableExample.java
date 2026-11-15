package examples.deadcode;

/**
 * Example for rule {@code CK-UNREACHABLE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy} guards a return with {@code if (true)}, so the
 * statement after the {@code if} can never run. The compiler accepts this, because it treats
 * every {@code if} as possibly skipping its branch.
 *
 * <p><b>What happens at runtime:</b> the statement is dead. Whoever reads the method assumes the
 * fallback runs, but the method always returns from the branch.
 *
 * <p><b>How to fix it:</b> delete the dead statement or the constant condition, as
 * {@link #fixed} shows.
 */
public class UnreachableExample {

  int buggy() {
    if (true) {
      return 1;
    }
    return 2; // :: CK-UNREACHABLE
  }

  int fixed() {
    return 1;
  }
}
