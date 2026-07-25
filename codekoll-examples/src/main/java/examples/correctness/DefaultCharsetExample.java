package examples.correctness;

import java.nio.charset.StandardCharsets;

/**
 * Example for rule {@code CK-DEFAULT-CHARSET}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} encodes a string with no-arg
 * {@code getBytes()}.
 *
 * <p><b>What happens at runtime:</b> the platform default charset is used — UTF-8 on the
 * Linux build server, Windows-1252 on a desktop, something else in an exotic container.
 * The same code produces different bytes on different machines: files written on one host
 * read as mojibake ("SmÃ¶rgÃ¥s") on another.
 *
 * <p><b>How to fix it:</b> name the charset explicitly, as {@link #fixed(String)} does.
 */
public class DefaultCharsetExample {

  public byte[] buggy(String text) {
    return text.getBytes(); // :: CK-DEFAULT-CHARSET
  }

  public byte[] fixed(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }
}
