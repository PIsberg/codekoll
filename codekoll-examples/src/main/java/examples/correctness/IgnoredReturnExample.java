package examples.correctness;

/**
 * Example for rule {@code CK-IGNORED-RETURN}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} calls {@code String.trim()} as a bare
 * statement. String is immutable — {@code trim()} returns a <em>new</em> string; it never
 * modifies the receiver.
 *
 * <p><b>What happens at runtime:</b> the trimmed result is computed and immediately thrown
 * away. The method returns the original, un-trimmed value — {@code "  Alice  "} stays
 * {@code "  Alice  "} — and downstream comparisons and lookups quietly fail.
 *
 * <p><b>How to fix it:</b> assign the result back, as {@link #fixed(String)} does.
 */
public class IgnoredReturnExample {

  public String buggy(String name) {
    name.trim(); // :: CK-IGNORED-RETURN
    return name;
  }

  public String fixed(String name) {
    name = name.trim();
    return name;
  }
}
