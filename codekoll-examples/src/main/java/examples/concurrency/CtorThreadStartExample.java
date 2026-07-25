package examples.concurrency;

/**
 * Example for rule {@code CK-CTOR-THREAD-START}.
 *
 * <p><b>What is wrong:</b> the {@code buggy} constructor starts its worker thread before
 * the constructor returns.
 *
 * <p><b>What happens at runtime:</b> the thread can begin running — and reading
 * {@code this} — while construction is still in progress. The JMM's final-field guarantees
 * apply only after the constructor completes, so on rare timing-dependent runs the worker
 * observes null/default fields: a heisenbug that appears under load and never in a
 * debugger.
 *
 * <p><b>How to fix it:</b> construct fully, then start from a separate method, as
 * {@code fixed} does.
 */
public class CtorThreadStartExample {

  static class buggy {
    private final String name;
    private final Thread worker;

    buggy(String name) {
      this.name = name;
      worker = new Thread(() -> System.out.println("hello " + this.name));
      worker.start(); // :: CK-CTOR-THREAD-START
    }
  }

  static class fixed {
    private final String name;
    private final Thread worker;

    fixed(String name) {
      this.name = name;
      worker = new Thread(() -> System.out.println("hello " + this.name));
    }

    void start() {
      worker.start();
    }
  }
}
