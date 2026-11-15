package examples.frameworks;

import java.util.concurrent.CompletableFuture;

/**
 * Example for rule {@code CK-ASYNC-THREAD-CONTEXT}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} records who placed an order on a background
 * thread, reading the current user from {@code SecurityContextHolder} inside the async task.
 * (The holder here is a stand-in for Spring Security's; codekoll matches by name.)
 *
 * <p><b>What happens at runtime:</b> the security context lives in a {@code ThreadLocal} filled
 * for the request thread. The {@code supplyAsync} task runs on a pool thread where it is empty,
 * so the audit log records {@code null} as the user for every order, or the code throws a
 * {@code NullPointerException} further down. The unit test calls the code on the test thread,
 * with the context set, and passes.
 *
 * <p><b>How to fix it:</b> read the context on the request thread and hand the value to the
 * task, as {@link #fixed(String)} does.
 */
public class AsyncThreadContextExample {

  static final class SecurityContextHolder {
    private static final ThreadLocal<String> USER = new ThreadLocal<>();

    private SecurityContextHolder() {}

    static String getContext() {
      return USER.get();
    }
  }

  public CompletableFuture<String> buggy(String orderId) {
    return CompletableFuture.supplyAsync(
        () -> orderId + " placed by " + SecurityContextHolder.getContext()); // :: CK-ASYNC-THREAD-CONTEXT
  }

  public CompletableFuture<String> fixed(String orderId) {
    String user = SecurityContextHolder.getContext();
    return CompletableFuture.supplyAsync(() -> orderId + " placed by " + user);
  }
}
