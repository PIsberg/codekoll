package examples.resources;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Example for rule {@code CK-FINALIZE}.
 *
 * <p><b>What is wrong:</b> the {@code buggy} class releases its stream in a
 * {@code finalize()} override.
 *
 * <p><b>What happens at runtime:</b> the JVM may run finalize arbitrarily late, on any
 * thread — or never. The file handle stays open unpredictably long; under load the process
 * exhausts handles. And finalization is deprecated for removal, so on a future JDK this
 * cleanup silently stops existing entirely.
 *
 * <p><b>How to fix it:</b> implement {@code AutoCloseable} and close deterministically, as
 * the {@code fixed} class does — callers use try-with-resources.
 */
public class FinalizeExample {

  static class buggy {
    private final FileInputStream stream;

    buggy(String path) throws IOException {
      stream = new FileInputStream(path);
    }

    @SuppressWarnings({"deprecation", "removal"}) // :: CK-FINALIZE
    protected void finalize() throws IOException {
      stream.close();
    }
  }

  static class fixed implements AutoCloseable {
    private final FileInputStream stream;

    fixed(String path) throws IOException {
      stream = new FileInputStream(path);
    }

    @Override
    public void close() throws IOException {
      stream.close();
    }
  }
}
