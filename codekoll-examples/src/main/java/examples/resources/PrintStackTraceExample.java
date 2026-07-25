package examples.resources;

/**
 * Example for rule {@code CK-PRINT-STACKTRACE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} handles a failure with
 * {@code e.printStackTrace()}.
 *
 * <p><b>What happens at runtime:</b> the trace goes to raw stderr — no timestamp, no request
 * correlation, and in most deployments not collected by the log aggregator at all. When the
 * incident is investigated, the "logged" failure is nowhere to be found.
 *
 * <p><b>How to fix it:</b> route through the application's error handling — rethrow wrapped
 * (as {@link #fixed()} does) or log via the project logger with the exception attached.
 */
public class PrintStackTraceExample {

  public void buggy() {
    try {
      process();
    } catch (IllegalStateException e) {
      e.printStackTrace(); // :: CK-PRINT-STACKTRACE
    }
  }

  public void fixed() {
    try {
      process();
    } catch (IllegalStateException e) {
      throw new RuntimeException("processing failed", e);
    }
  }

  private void process() {
    throw new IllegalStateException("boom");
  }
}
