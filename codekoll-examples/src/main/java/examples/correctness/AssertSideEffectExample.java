package examples.correctness;

import java.util.HashSet;
import java.util.Set;

/**
 * Example for rule {@code CK-ASSERT-SIDE-EFFECT}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(String)} registers the order id by calling
 * {@code processed.add(orderId)} <em>inside</em> an {@code assert}. The duplicate check and
 * the state change that makes it work are the same expression.
 *
 * <p><b>What happens at runtime:</b> assertions are disabled unless the JVM is started with
 * {@code -ea}. Tests normally enable them, so the set fills up and duplicates are caught, and
 * the code looks correct. Production normally does not, so the whole expression is skipped:
 * {@code processed} stays empty forever and every order is treated as new. Nothing throws and
 * nothing is logged — the duplicate-shipment bug simply appears in production only.
 *
 * <p><b>How to fix it:</b> perform the state change unconditionally and assert on its result,
 * as {@link #fixed(String)} does. The assert then only reads, so disabling it changes nothing
 * but the check itself.
 */
public class AssertSideEffectExample {

  private final Set<String> processed = new HashSet<>();

  public String buggy(String orderId) {
    assert processed.add(orderId) : "duplicate order " + orderId; // :: CK-ASSERT-SIDE-EFFECT
    return "shipped " + orderId;
  }

  public String fixed(String orderId) {
    boolean firstTime = processed.add(orderId);
    assert firstTime : "duplicate order " + orderId;
    return "shipped " + orderId;
  }
}
