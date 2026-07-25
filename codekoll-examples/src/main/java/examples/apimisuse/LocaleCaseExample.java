package examples.apimisuse;

import java.util.Locale;

/**
 * Example for rule {@code CK-LOCALE-CASE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} lowercases a protocol scheme with no-arg
 * {@code toLowerCase()}.
 *
 * <p><b>What happens at runtime:</b> the default locale governs. On a Turkish-locale server
 * {@code "HTTPS".toLowerCase()} yields {@code "https"} with a dotless i for the wrong
 * letters — "HTTP" becomes "http" but "TITLE" becomes "tıtle" — so scheme and header
 * comparisons fail for users in certain regions and nowhere else.
 *
 * <p><b>How to fix it:</b> pass {@code Locale.ROOT} for machine-facing text, as
 * {@link #fixed(String)} does.
 */
public class LocaleCaseExample {

  public boolean buggy(String scheme) {
    return scheme.toLowerCase().equals("https"); // :: CK-LOCALE-CASE
  }

  public boolean fixed(String scheme) {
    return scheme.toLowerCase(Locale.ROOT).equals("https");
  }
}
