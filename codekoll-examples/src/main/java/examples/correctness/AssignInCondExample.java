package examples.correctness;

/**
 * Example for rule {@code CK-ASSIGN-IN-COND}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(boolean)} tests completion with
 * {@code if (finished = true)} — one {@code =} short of a comparison.
 *
 * <p><b>What happens at runtime:</b> the condition ASSIGNS true to {@code finished} and
 * then branches on the assigned value: the branch always runs, and the variable's real
 * state is clobbered as a side effect. Completion is reported for work that never finished.
 *
 * <p><b>How to fix it:</b> compare — or better, branch on the boolean directly — as
 * {@link #fixed(boolean)} does.
 */
public class AssignInCondExample {

  public String buggy(boolean finished) {
    if (finished = true) { // :: CK-ASSIGN-IN-COND
      return "done";
    }
    return "pending";
  }

  public String fixed(boolean finished) {
    if (finished) {
      return "done";
    }
    return "pending";
  }
}
