package examples.correctness;

/**
 * Example for rule {@code CK-SELF-ASSIGN}.
 *
 * <p><b>What is wrong:</b> the constructor in {@link #buggy} assigns {@code this.name = name}
 * — but there is no {@code name} parameter in scope, so both sides resolve to the field:
 * the field is assigned to itself.
 *
 * <p><b>What happens at runtime:</b> the assignment does nothing. The field silently keeps
 * its default value (null), and the bug surfaces far away as a NullPointerException the
 * first time the "initialized" value is used.
 *
 * <p><b>How to fix it:</b> assign from the intended parameter, as {@link #fixed} shows.
 */
public class SelfAssignExample {

  static class buggy {
    private String name;

    buggy(String value) {
      name = name; // :: CK-SELF-ASSIGN
      System.out.println(value);
    }
  }

  static class fixed {
    private String name;

    fixed(String name) {
      this.name = name;
    }

    String name() {
      return name;
    }
  }
}
