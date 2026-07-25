package examples.resources;

/**
 * Example for rule {@code CK-CATCH-BROAD}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Runnable)} wraps a task in
 * {@code catch (Throwable)}.
 *
 * <p><b>What happens at runtime:</b> Throwable includes OutOfMemoryError,
 * StackOverflowError and linkage errors — states the application cannot recover from.
 * Catching them keeps a half-broken JVM limping: the clear crash that would have paged
 * someone becomes hours of confusing downstream corruption instead.
 *
 * <p><b>How to fix it:</b> catch {@code Exception} (or narrower) and let Errors propagate,
 * as {@link #fixed(Runnable)} does.
 */
public class CatchBroadExample {

  public void buggy(Runnable task) {
    try {
      task.run();
    } catch (Throwable t) { // :: CK-CATCH-BROAD
      System.out.println("task failed: " + t.getMessage());
    }
  }

  public void fixed(Runnable task) {
    try {
      task.run();
    } catch (Exception e) {
      System.out.println("task failed: " + e.getMessage());
    }
  }
}
