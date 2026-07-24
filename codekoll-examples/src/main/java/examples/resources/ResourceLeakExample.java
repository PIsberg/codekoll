package examples.resources;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Example for rule {@code CK-RESOURCE-LEAK}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} opens a {@code FileInputStream} and never
 * closes it — no try-with-resources, no {@code close()} in a {@code finally}.
 *
 * <p><b>What happens at runtime:</b> every call leaks one operating-system file handle.
 * Nothing fails immediately; hours or days later the process hits its handle limit and every
 * subsequent open fails with "too many open files" — the classic slow-burn production
 * outage.
 *
 * <p><b>How to fix it:</b> try-with-resources, as {@link #fixed(String)} does — the stream
 * closes on every path, including exceptions.
 */
public class ResourceLeakExample {

  public int buggy(String path) throws IOException {
    FileInputStream stream = new FileInputStream(path); // :: CK-RESOURCE-LEAK
    return stream.read();
  }

  public int fixed(String path) throws IOException {
    try (FileInputStream stream = new FileInputStream(path)) {
      return stream.read();
    }
  }
}
