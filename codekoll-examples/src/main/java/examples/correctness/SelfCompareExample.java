package examples.correctness;

/**
 * Example for rule {@code CK-SELF-COMPARE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy} compares {@code a.priority} with itself — a
 * copy-paste slip; the second operand was meant to be {@code b.priority}.
 *
 * <p><b>What happens at runtime:</b> the comparison is constant, so the sort tie-breaker it
 * implements never distinguishes any two elements: ordering is silently wrong, no exception,
 * no log line.
 *
 * <p><b>How to fix it:</b> compare against the intended other operand, as {@link #fixed}
 * does.
 */
public class SelfCompareExample {

  static class Task {
    int priority;
  }

  public boolean buggy(Task a, Task b) {
    return a.priority > a.priority; // :: CK-SELF-COMPARE
  }

  public boolean fixed(Task a, Task b) {
    return a.priority > b.priority;
  }
}
