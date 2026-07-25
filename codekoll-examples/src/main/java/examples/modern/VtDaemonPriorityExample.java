package examples.modern;

/**
 * Example for rule {@code CK-VT-DAEMON-PRIORITY}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Runnable)} tries to keep the JVM alive by calling
 * {@code setDaemon(false)} on a virtual thread.
 *
 * <p><b>What happens at runtime:</b> virtual threads are ALWAYS daemons —
 * {@code setDaemon(false)} throws {@code IllegalArgumentException} on every execution.
 * (And {@code setPriority} on one doesn't throw: it is silently ignored.)
 *
 * <p><b>How to fix it:</b> keep the JVM alive by joining or using a platform thread for
 * the lifecycle, as {@link #fixed(Runnable)} does.
 */
public class VtDaemonPriorityExample {

  public void buggy(Runnable task) {
    Thread worker = Thread.ofVirtual().unstarted(task);
    Thread.ofVirtual().unstarted(task).setDaemon(false); // :: CK-VT-DAEMON-PRIORITY
    worker.start();
  }

  public void fixed(Runnable task) throws InterruptedException {
    Thread worker = Thread.startVirtualThread(task);
    worker.join();
  }
}
