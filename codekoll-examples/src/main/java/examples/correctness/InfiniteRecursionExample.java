package examples.correctness;

/**
 * Example for rule {@code CK-INFINITE-RECURSION}.
 *
 * <p><b>What is wrong:</b> the {@code buggy} getter returns {@code getName()} — itself —
 * instead of the field {@code name} (a classic IDE-generated-getter typo).
 *
 * <p><b>What happens at runtime:</b> every call makes another identical call with no base
 * case: {@code StackOverflowError} on the very first invocation. Serialization or logging
 * that touches the getter takes the whole request down.
 *
 * <p><b>How to fix it:</b> return the field, as {@link #Fixed} does.
 */
public class InfiniteRecursionExample {

  static class buggy {
    private String name;

    String getName() {
      return getName(); // :: CK-INFINITE-RECURSION
    }
  }

  static class Fixed {
    private String name;

    String getName() {
      return name;
    }

    String fixed() {
      return getName();
    }
  }
}
