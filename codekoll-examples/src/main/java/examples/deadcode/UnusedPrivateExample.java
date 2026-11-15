package examples.deadcode;

/**
 * Example for rule {@code CK-UNUSED-PRIVATE}.
 *
 * <p><b>What is wrong:</b> the class in {@link #buggy} declares a private method that nothing in
 * the file calls.
 *
 * <p><b>What happens at runtime:</b> nothing runs it. It still has to be read, compiled and
 * kept in step with refactorings.
 *
 * <p><b>How to fix it:</b> delete it, or call it, as {@link #fixed} does.
 */
public class UnusedPrivateExample {

  static class buggy {
    private void helper() {} // :: CK-UNUSED-PRIVATE
  }

  static class Fixed {
    private void helper() {}

    void fixed() {
      helper();
    }
  }
}
