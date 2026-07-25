package examples.nullness;

import java.util.Optional;

/**
 * Example for rule {@code CK-OPTIONAL-GET-BARE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} chains {@code .get()} directly onto the
 * Optional-returning lookup.
 *
 * <p><b>What happens at runtime:</b> on the absent case, {@code NoSuchElementException} —
 * the same crash a null would have caused, minus Optional's protection. The chain asserts
 * "this user always exists" without handling "but what if not", exactly the assumption
 * Optional exists to make explicit.
 *
 * <p><b>How to fix it:</b> state the absent case, as {@link #fixed(String)} does with a
 * meaningful exception.
 */
public class OptionalGetBareExample {

  public String buggy(String userId) {
    return findName(userId).get(); // :: CK-OPTIONAL-GET-BARE
  }

  public String fixed(String userId) {
    return findName(userId)
        .orElseThrow(() -> new IllegalArgumentException("no such user: " + userId));
  }

  private Optional<String> findName(String userId) {
    return userId.isEmpty() ? Optional.empty() : Optional.of("user-" + userId);
  }
}
