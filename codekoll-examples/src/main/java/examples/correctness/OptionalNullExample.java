package examples.correctness;

import java.util.Optional;

/**
 * Example for rule {@code CK-OPTIONAL-NULL}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} returns {@code null} from a method whose
 * declared return type is {@code Optional<String>}.
 *
 * <p><b>What happens at runtime:</b> Optional's whole point is that absence is a value, so
 * callers write {@code find(id).isPresent()} or {@code find(id).map(...)} and never
 * null-check — and exactly those safe-looking call sites throw NullPointerException.
 *
 * <p><b>How to fix it:</b> return {@code Optional.empty()} for the absent case, as
 * {@link #fixed(String)} does.
 */
public class OptionalNullExample {

  public Optional<String> buggy(String id) {
    if (id.isEmpty()) {
      return null; // :: CK-OPTIONAL-NULL
    }
    return Optional.of(id);
  }

  public Optional<String> fixed(String id) {
    if (id.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(id);
  }
}
