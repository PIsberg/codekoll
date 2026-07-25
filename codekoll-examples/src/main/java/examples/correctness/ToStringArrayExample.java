package examples.correctness;

import java.util.Arrays;

/**
 * Example for rule {@code CK-TOSTRING-ARRAY}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String[])} builds a log message by concatenating
 * an array with a string.
 *
 * <p><b>What happens at runtime:</b> the array's inherited {@code toString} yields
 * {@code [Ljava.lang.String;@1a2b3c} — a type sigil and identity hash, never the contents.
 * The log line meant to record which items failed shows a useless address instead, exactly
 * when someone needs the data.
 *
 * <p><b>How to fix it:</b> {@code Arrays.toString}, as {@link #fixed(String[])} does.
 */
public class ToStringArrayExample {

  public String buggy(String[] failedItems) {
    return "failed to process: " + failedItems; // :: CK-TOSTRING-ARRAY
  }

  public String fixed(String[] failedItems) {
    return "failed to process: " + Arrays.toString(failedItems);
  }
}
