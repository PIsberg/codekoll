package examples.correctness;

/**
 * Example for rule {@code CK-EXCEPTION-NOT-THROWN}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(int)} constructs an {@code IllegalArgumentException}
 * for invalid input — but the {@code throw} keyword is missing.
 *
 * <p><b>What happens at runtime:</b> the exception object is created, immediately discarded,
 * and execution continues past the validation as if the input were fine. Negative ages flow
 * straight into the rest of the system.
 *
 * <p><b>How to fix it:</b> add the {@code throw} keyword, as {@link #fixed(int)} does.
 */
public class ExceptionNotThrownExample {

  private int age;

  public void buggy(int age) {
    if (age < 0) {
      new IllegalArgumentException("age must be >= 0: " + age); // :: CK-EXCEPTION-NOT-THROWN
    }
    this.age = age;
  }

  public void fixed(int age) {
    if (age < 0) {
      throw new IllegalArgumentException("age must be >= 0: " + age);
    }
    this.age = age;
  }

  public int age() {
    return age;
  }
}
