package examples.concurrency;

/**
 * Example for rule {@code CK-DCL-NO-VOLATILE}.
 *
 * <p><b>What is wrong:</b> the {@code buggy} singleton uses double-checked locking, but the
 * {@code instance} field is not {@code volatile}.
 *
 * <p><b>What happens at runtime:</b> without volatile the JMM permits a thread to observe a
 * non-null {@code instance} whose constructor has not finished — the reference write can be
 * reordered ahead of the field writes. A second thread skips the lock, uses the
 * half-constructed object, and fails intermittently in ways no debugger reproduces.
 *
 * <p><b>How to fix it:</b> declare the field {@code volatile}, as the {@code fixed}
 * singleton does (a static holder class works too).
 */
public class DclNoVolatileExample {

  static class buggy {
    private static buggy instance;
    private static final Object LOCK = new Object();

    static buggy get() {
      if (instance == null) { // :: CK-DCL-NO-VOLATILE
        synchronized (LOCK) {
          if (instance == null) {
            instance = new buggy();
          }
        }
      }
      return instance;
    }
  }

  static class fixed {
    private static volatile fixed instance;
    private static final Object LOCK = new Object();

    static fixed get() {
      if (instance == null) {
        synchronized (LOCK) {
          if (instance == null) {
            instance = new fixed();
          }
        }
      }
      return instance;
    }
  }
}
