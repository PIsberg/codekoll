package examples.correctness;

/**
 * Example for rule {@code CK-FORMAT-MISMATCH}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String, int)} has three {@code %} conversions in
 * the format string but passes only two arguments.
 *
 * <p><b>What happens at runtime:</b> {@code String.format} counts conversions at runtime;
 * the missing third argument throws {@code MissingFormatArgumentException}. It crashes
 * exactly the error message it was meant to build — typically while another failure is
 * already being handled, replacing a useful log line with a formatting stack trace.
 *
 * <p><b>How to fix it:</b> supply one argument per conversion, as
 * {@link #fixed(String, int, int)} does.
 */
public class FormatMismatchExample {

  public String buggy(String user, int count) {
    return String.format("user %s has %d items and %d pending", user, count); // :: CK-FORMAT-MISMATCH
  }

  public String fixed(String user, int count, int pending) {
    return String.format("user %s has %d items and %d pending", user, count, pending);
  }
}
