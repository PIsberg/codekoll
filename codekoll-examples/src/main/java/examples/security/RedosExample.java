package examples.security;

import java.util.regex.Pattern;

/**
 * Example for rule {@code CK-REDOS}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} validates input with the regex
 * {@code (\\s*\\w+)*$} — a quantifier wrapping an already-quantified group.
 *
 * <p><b>What happens at runtime:</b> on a non-matching input the engine tries exponentially
 * many ways to split the string. A crafted value of a few dozen characters pins a CPU core
 * for minutes: one request becomes a denial of service (ReDoS).
 *
 * <p><b>How to fix it:</b> rewrite without nested quantifiers (or use a possessive
 * quantifier), as {@link #fixed(String)} does.
 */
public class RedosExample {

  private static final Pattern BUGGY = Pattern.compile("(\\s*\\w+)*$"); // :: CK-REDOS
  private static final Pattern FIXED = Pattern.compile("\\s*\\w[\\w\\s]*");

  public boolean buggy(String input) {
    return BUGGY.matcher(input).matches();
  }

  public boolean fixed(String input) {
    return FIXED.matcher(input).matches();
  }
}
