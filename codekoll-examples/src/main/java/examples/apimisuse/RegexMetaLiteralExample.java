package examples.apimisuse;

/**
 * Example for rule {@code CK-REGEX-META-LITERAL}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} splits a filename on {@code "."} —
 * forgetting that {@code split} takes a regex, where {@code .} matches any character.
 *
 * <p><b>What happens at runtime:</b> every character matches the separator, so the result
 * is an <em>empty array</em>. The extension check below never finds anything; files are
 * silently misclassified.
 *
 * <p><b>How to fix it:</b> escape the dot, as {@link #fixed(String)} does (or use
 * {@code Pattern.quote(".")}).
 */
public class RegexMetaLiteralExample {

  public String buggy(String filename) {
    String[] parts = filename.split("."); // :: CK-REGEX-META-LITERAL
    return parts.length > 1 ? parts[parts.length - 1] : "";
  }

  public String fixed(String filename) {
    String[] parts = filename.split("\\.");
    return parts.length > 1 ? parts[parts.length - 1] : "";
  }
}
