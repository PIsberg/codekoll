package examples.resources;

/**
 * Example for rule {@code CK-CATCH-NPE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} handles a possibly-null value by catching
 * {@code NullPointerException}.
 *
 * <p><b>What happens at runtime:</b> the catch absorbs EVERY NPE from the whole try body —
 * including ones from unrelated code added later. The specific null case is "handled", the
 * underlying bug stays unfixed, and future null bugs get silently swallowed with it,
 * surfacing as mysteriously empty results instead of clear crashes.
 *
 * <p><b>How to fix it:</b> null-check the specific access, as {@link #fixed(String)} does.
 */
public class CatchNpeExample {

  public String buggy(String value) {
    try {
      return value.trim().toUpperCase(java.util.Locale.ROOT);
    } catch (NullPointerException e) { // :: CK-CATCH-NPE
      return "";
    }
  }

  public String fixed(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toUpperCase(java.util.Locale.ROOT);
  }
}
