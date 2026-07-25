package examples.resources;

/**
 * Example for rule {@code CK-LOST-CAUSE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} catches the low-level failure and throws a new
 * exception WITHOUT passing the original as the cause.
 *
 * <p><b>What happens at runtime:</b> the original stack trace — the file, line and message
 * of what actually failed — is discarded forever. Production logs show only "operation
 * failed" pointing at the catch block; the 2 a.m. investigation has nothing to go on.
 *
 * <p><b>How to fix it:</b> pass the caught exception as the cause, as {@link #fixed()}
 * does — wrapper and root cause both survive.
 */
public class LostCauseExample {

  public void buggy() {
    try {
      parseConfig();
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("configuration invalid"); // :: CK-LOST-CAUSE
    }
  }

  public void fixed() {
    try {
      parseConfig();
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("configuration invalid", e);
    }
  }

  private void parseConfig() {
    throw new IllegalArgumentException("port out of range: 99999");
  }
}
