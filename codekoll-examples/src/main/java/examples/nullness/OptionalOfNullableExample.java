package examples.nullness;

import java.util.Map;
import java.util.Optional;

/**
 * Example for rule {@code CK-OPTIONAL-OF-NULLABLE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Map, String)} wraps {@code settings.get(key)} in
 * {@code Optional.of}.
 *
 * <p><b>What happens at runtime:</b> {@code Optional.of} throws {@code NullPointerException}
 * when its argument is null, and {@code Map.get} returns null on a miss. The method meant
 * to model "maybe present" instead crashes on exactly the absent case it was written for.
 *
 * <p><b>How to fix it:</b> use {@code Optional.ofNullable}, as {@link #fixed(Map, String)}
 * does.
 */
public class OptionalOfNullableExample {

  public Optional<String> buggy(Map<String, String> settings, String key) {
    return Optional.of(settings.get(key)); // :: CK-OPTIONAL-OF-NULLABLE
  }

  public Optional<String> fixed(Map<String, String> settings, String key) {
    return Optional.ofNullable(settings.get(key));
  }
}
