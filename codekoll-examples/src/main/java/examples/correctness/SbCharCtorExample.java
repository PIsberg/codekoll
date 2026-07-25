package examples.correctness;

/**
 * Example for rule {@code CK-SB-CHAR-CTOR}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} calls {@code new StringBuilder('(')} expecting a
 * builder that starts with the character.
 *
 * <p><b>What happens at runtime:</b> StringBuilder has no char constructor — the char
 * silently widens to {@code int} and picks the <em>capacity</em> constructor. The builder is
 * empty (capacity 40); the opening parenthesis never appears in the output, producing
 * malformed strings like {@code "a, b)"}.
 *
 * <p><b>How to fix it:</b> use a string literal or {@code append(char)}, as {@link #fixed()}
 * does.
 */
public class SbCharCtorExample {

  public String buggy() {
    StringBuilder sb = new StringBuilder('('); // :: CK-SB-CHAR-CTOR
    return sb.append("a, b").append(')').toString();
  }

  public String fixed() {
    StringBuilder sb = new StringBuilder("(");
    return sb.append("a, b").append(')').toString();
  }
}
