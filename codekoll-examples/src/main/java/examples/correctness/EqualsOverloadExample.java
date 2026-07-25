package examples.correctness;

/**
 * Example for rule {@code CK-EQUALS-OVERLOAD}.
 *
 * <p><b>What is wrong:</b> the {@code buggy} class declares
 * {@code equals(EqualsOverloadExample.buggy)} — a specific parameter type — without
 * overriding {@code equals(Object)}.
 *
 * <p><b>What happens at runtime:</b> that method is a new overload, not an override.
 * Collections, {@code Objects.equals} and all generic code call {@code equals(Object)},
 * which still uses identity — so a HashSet treats two "equal" instances as different and
 * accumulates duplicates. Only hand-written calls with a statically-typed argument see the
 * intended logic.
 *
 * <p><b>How to fix it:</b> override {@code equals(Object)} (with {@code @Override} so the
 * compiler verifies the signature), as {@code fixed} does.
 */
public class EqualsOverloadExample {

  static class buggy {
    int id;

    public boolean equals(buggy other) { // :: CK-EQUALS-OVERLOAD
      return other != null && id == other.id;
    }
  }

  static class fixed {
    int id;

    @Override
    public boolean equals(Object o) {
      return o instanceof fixed other && id == other.id;
    }

    @Override
    public int hashCode() {
      return id;
    }

    boolean fixed(Object o) {
      return equals(o);
    }
  }
}
