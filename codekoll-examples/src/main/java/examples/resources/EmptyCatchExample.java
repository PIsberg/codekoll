package examples.resources;

/**
 * Example for rule {@code CK-EMPTY-CATCH}.
 *
 * <p><b>What is wrong:</b> the {@code catch} block in {@link #buggy()} is empty, so the
 * payment failure is swallowed without any trace.
 *
 * <p><b>What happens at runtime:</b> nothing — and that is the bug. The exception vanishes,
 * the program continues as if the payment succeeded, and the failure is undiagnosable in
 * production logs.
 *
 * <p><b>How to fix it:</b> handle the exception: log it and/or rethrow it wrapped with the
 * original as cause, as {@link #fixed()} does. If dropping it is truly intentional, name the
 * variable {@code ignored}.
 */
public class EmptyCatchExample {

  public void buggy() {
    try {
      processPayment();
    } catch (IllegalStateException e) { // :: CK-EMPTY-CATCH
    }
  }

  public void fixed() {
    try {
      processPayment();
    } catch (IllegalStateException e) {
      // The exception is preserved as the cause: message and stack trace survive.
      throw new RuntimeException("payment failed", e);
    }
  }

  private void processPayment() {
    throw new IllegalStateException("card declined");
  }
}
