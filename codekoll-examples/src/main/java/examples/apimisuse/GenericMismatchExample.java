package examples.apimisuse;

import java.util.HashMap;
import java.util.Map;

/**
 * Example for rule {@code CK-GENERIC-MISMATCH}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} calls {@code get(12345)} — an {@code int} —
 * on a map whose keys are {@code String}s. {@code Map.get} takes {@code Object} for
 * backward compatibility, so the compiler happily accepts it.
 *
 * <p><b>What happens at runtime:</b> the boxed {@code Integer 12345} can never equal any
 * {@code String} key, so the lookup always returns null. The bug presents as an eternal
 * cache miss: no exception, no log line, just data that is never found.
 *
 * <p><b>How to fix it:</b> pass the key in the map's key type, as {@link #fixed()} does.
 */
public class GenericMismatchExample {

  private final Map<String, String> userCache = new HashMap<>();

  public String buggy() {
    userCache.put("12345", "Alice");
    return userCache.get(12345); // :: CK-GENERIC-MISMATCH
  }

  public String fixed() {
    userCache.put("12345", "Alice");
    return userCache.get("12345");
  }
}
