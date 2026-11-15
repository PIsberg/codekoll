package examples.deadcode;

/**
 * Example for rule {@code CK-DEAD-BRANCH}.
 *
 * <p><b>What is wrong:</b> {@link #buggy} guards a block with the literal condition
 * {@code false}, so the block can never run.
 *
 * <p><b>What happens at runtime:</b> nothing: the block is skipped on every call, so the code
 * in it is never exercised and rots unnoticed.
 *
 * <p><b>How to fix it:</b> delete the block, or, to switch code off on purpose, guard it with a
 * named constant as {@link #fixed} does; named constants are not reported.
 */
public class DeadBranchExample {

  private static final boolean DEBUG = false;

  void buggy() {
    if (false) { // :: CK-DEAD-BRANCH
      System.out.println("never printed");
    }
  }

  void fixed() {
    if (DEBUG) {
      System.out.println("switched off on purpose");
    }
  }
}
