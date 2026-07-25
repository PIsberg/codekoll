package examples.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Example for rule {@code CK-NATIVE-DESERIAL}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(InputStream)} deserializes an incoming request
 * body with {@code ObjectInputStream.readObject()}.
 *
 * <p><b>What happens at runtime:</b> readObject instantiates whatever classes the byte
 * stream names and runs their deserialization hooks. Well-known "gadget chains" in common
 * libraries turn this into arbitrary code execution — if an attacker controls any byte of
 * the stream, they control the JVM.
 *
 * <p><b>How to fix it:</b> use a data format (JSON, protobuf) as {@link #fixed(InputStream)}
 * sketches — or, when native serialization must stay, install a strict
 * {@code ObjectInputFilter} allowlist before reading.
 */
public class NativeDeserialExample {

  public Object buggy(InputStream requestBody) throws IOException, ClassNotFoundException {
    try (ObjectInputStream in = new ObjectInputStream(requestBody)) {
      return in.readObject(); // :: CK-NATIVE-DESERIAL
    }
  }

  public Object fixed(InputStream requestBody) throws IOException {
    // Parse a data format instead: bytes are data, never executable class descriptions.
    return new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
  }
}
