package examples.correctness;

/**
 * Example for rule {@code CK-REF-EQUALITY}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} compares Strings with {@code ==}, which
 * compares object identity (memory addresses), not contents.
 *
 * <p><b>What happens at runtime:</b> two equal strings are frequently different objects —
 * anything read from a file, a database, user input, or built at runtime. The comparison is
 * then false even though the text matches: the job never starts, and no error explains why.
 * (It often "works" in tests, where both sides are compile-time literals interned to the
 * same object.)
 *
 * <p><b>How to fix it:</b> compare values with {@code equals}, as {@link #fixed(String)}
 * does ({@code Objects.equals} if either side may be null).
 */
public class RefEqualityExample {

  public boolean buggy(String status) {
    return status == "ACTIVE"; // :: CK-REF-EQUALITY
  }

  public boolean fixed(String status) {
    return "ACTIVE".equals(status);
  }
}
