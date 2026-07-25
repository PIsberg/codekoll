package examples.correctness;

import java.util.Arrays;

/**
 * Example for rule {@code CK-ARRAY-OBJECT-METHODS}.
 *
 * <p><b>What is wrong:</b> {@link #buggy} compares two arrays with {@code equals()} and logs
 * one with implicit {@code toString()}.
 *
 * <p><b>What happens at runtime:</b> arrays inherit Object's identity implementations:
 * {@code a.equals(b)} is reference comparison — two arrays with identical contents are NOT
 * equal — and printing yields {@code [B@1a2b3c} instead of the contents. The checksum
 * verification below never passes for a freshly computed array.
 *
 * <p><b>How to fix it:</b> use {@code java.util.Arrays} helpers, as {@link #fixed} does.
 */
public class ArrayObjectMethodsExample {

  public boolean buggy(byte[] expected, byte[] actual) {
    boolean match = expected.equals(actual); // :: CK-ARRAY-OBJECT-METHODS
    if (!match) {
      System.out.println("checksum mismatch: " + actual.toString()); // :: CK-ARRAY-OBJECT-METHODS
    }
    return match;
  }

  public boolean fixed(byte[] expected, byte[] actual) {
    boolean match = Arrays.equals(expected, actual);
    if (!match) {
      System.out.println("checksum mismatch: " + Arrays.toString(actual));
    }
    return match;
  }
}
