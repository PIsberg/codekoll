package examples.frameworks;

import org.slf4j.Logger;

/**
 * Example for rule {@code CK-LOG-EXCEPTION-LOST}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Logger, String)} logs the failure with
 * {@code "..." + e.getMessage()}.
 *
 * <p><b>What happens at runtime:</b> only the message text is recorded — the stack trace,
 * the single most useful part of a failure, is gone. And {@code getMessage()} is frequently
 * null (NPEs, many wrapper exceptions), so the log line can read literally "import failed:
 * null" — worse than useless during an incident.
 *
 * <p><b>How to fix it:</b> pass the exception as the last logger argument, as
 * {@link #fixed(Logger, String)} does — SLF4J attaches the full stack trace.
 */
public class LogExceptionLostExample {

  public void buggy(Logger log, String orderId) {
    try {
      process(orderId);
    } catch (RuntimeException e) {
      log.error("import failed for " + orderId + ": " + e.getMessage()); // :: CK-LOG-EXCEPTION-LOST
    }
  }

  public void fixed(Logger log, String orderId) {
    try {
      process(orderId);
    } catch (RuntimeException e) {
      log.error("import failed for {}", orderId, e);
    }
  }

  private void process(String orderId) {
    throw new IllegalStateException("bad record: " + orderId);
  }
}
