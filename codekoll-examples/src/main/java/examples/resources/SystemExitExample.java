package examples.resources;

/**
 * Example for rule {@code CK-SYSTEM-EXIT}.
 *
 * <p><b>What is wrong:</b> {@code buggy}'s error path calls {@code System.exit(1)} from
 * service-layer code.
 *
 * <p><b>What happens at runtime:</b> the ENTIRE JVM terminates — every in-flight request,
 * every other component, the application server hosting this code. One malformed record
 * becomes a full outage, and shutdown skips the cleanup other threads' finally blocks
 * would have done.
 *
 * <p><b>How to fix it:</b> throw and let the actual entry point decide the process's fate,
 * as {@code fixed} does.
 */
public class SystemExitExample {

  static class buggyImportService {
    void importRecord(String record) {
      if (record.isEmpty()) {
        System.exit(1); // :: CK-SYSTEM-EXIT
      }
    }
  }

  static class fixedImportService {
    void importRecord(String record) {
      if (record.isEmpty()) {
        throw new IllegalArgumentException("empty record");
      }
    }

    void fixed(String record) {
      importRecord(record);
    }
  }
}
