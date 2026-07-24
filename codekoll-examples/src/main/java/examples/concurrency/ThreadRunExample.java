package examples.concurrency;

/**
 * Example for rule {@code CK-THREAD-RUN}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} calls {@code Thread.run()} instead of
 * {@code start()}.
 *
 * <p><b>What happens at runtime:</b> {@code run()} is a plain method call — the task executes
 * synchronously on the <em>current</em> thread. No new thread is ever started, so the code
 * silently loses all the concurrency it was written for (and any "background" work now blocks
 * the caller).
 *
 * <p><b>How to fix it:</b> call {@code start()}, which launches the new thread and invokes
 * {@code run()} on it, as {@link #fixed()} does.
 */
public class ThreadRunExample {

  public void buggy(Runnable task) {
    Thread worker = new Thread(task);
    worker.run(); // :: CK-THREAD-RUN
  }

  public void fixed(Runnable task) {
    Thread worker = new Thread(task);
    worker.start();
  }
}
