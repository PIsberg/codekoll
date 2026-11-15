package examples.deadcode;

/**
 * Example for rule {@code CK-UNUSED-LOCAL}.
 *
 * <p><b>What is wrong:</b> {@link #buggy} declares a local variable and never reads it.
 *
 * <p><b>What happens at runtime:</b> the value is computed and thrown away. Often it is a
 * leftover, or the result that was meant to be used.
 *
 * <p><b>How to fix it:</b> delete it, or use it, as {@link #fixed} does.
 */
public class UnusedLocalExample {

  void buggy() {
    int leftover = 5; // :: CK-UNUSED-LOCAL
  }

  int fixed() {
    int used = 5;
    return used;
  }
}
