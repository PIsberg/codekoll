package examples.apimisuse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Example for rule {@code CK-REGEX-GROUP-INDEX}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} reads the status code out of an access-log
 * line with {@code matcher.group(3)}, but the pattern has only two <em>capturing</em> groups.
 * The leading {@code (?:GET|POST)} looks like a third — it is a non-capturing group and is
 * never numbered.
 *
 * <p><b>What happens at runtime:</b> the compiler has no opinion about group numbers, so this
 * builds and deploys. The first line that actually matches throws
 * {@code IndexOutOfBoundsException: No group 3}. The same trap springs the other way round when
 * someone later adds a group to the middle of a pattern: every reference after it silently
 * shifts by one and starts returning the wrong field instead of throwing.
 *
 * <p><b>How to fix it:</b> count only the capturing groups — {@code ( … )} and
 * {@code (?<name> … )} — as {@link #fixed(String)} does. Better still, use named groups
 * ({@code (?<status>\d{3})} with {@code matcher.group("status")}): names do not renumber when
 * the pattern grows.
 */
public class RegexGroupIndexExample {

  private static final Pattern LOG_LINE = Pattern.compile("(?:GET|POST) (/\\S+) (\\d{3})");

  public String buggy(String line) {
    Matcher matcher = LOG_LINE.matcher(line);
    if (!matcher.matches()) {
      return "unparsed";
    }
    return matcher.group(3); // :: CK-REGEX-GROUP-INDEX
  }

  public String fixed(String line) {
    Matcher matcher = LOG_LINE.matcher(line);
    if (!matcher.matches()) {
      return "unparsed";
    }
    return matcher.group(2);
  }
}
