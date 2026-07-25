package examples.frameworks;

import org.slf4j.Logger;

/**
 * Example for rule {@code CK-SLF4J-PLACEHOLDER}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Logger, String, long)} logs two values with only
 * one {@code {}} placeholder.
 *
 * <p><b>What happens at runtime:</b> SLF4J substitutes placeholders positionally and
 * silently ignores the mismatch — the amount never appears in the log line. The order id
 * that operations needs during the incident prints, the amount is simply gone; nobody
 * notices until the log is the only evidence.
 *
 * <p><b>How to fix it:</b> one {@code {}} per argument, as
 * {@link #fixed(Logger, String, long)} does (a trailing Throwable is extra and gets the
 * stack trace).
 */
public class Slf4jPlaceholderExample {

  public void buggy(Logger log, String orderId, long amount) {
    log.info("processing order {}", orderId, amount); // :: CK-SLF4J-PLACEHOLDER
  }

  public void fixed(Logger log, String orderId, long amount) {
    log.info("processing order {} amount {}", orderId, amount);
  }
}
