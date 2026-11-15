package examples.frameworks;

import java.util.List;
import java.util.stream.Stream;

/**
 * Example for rule {@code CK-TX-LAZY-RETURN}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} is {@code @Transactional} and returns the repository's
 * streaming query to its caller. (The annotation and repository are stand-ins for Spring's and
 * Spring Data's; codekoll matches by name.)
 *
 * <p><b>What happens at runtime:</b> the transaction commits and the persistence context closes
 * as {@code buggy()} returns, before the caller pulls the first order. Consuming the stream reads
 * from a closed {@code ResultSet} or session and fails, and any lazy association touched along the
 * way throws {@code LazyInitializationException}. The method's own test passes, because the test
 * framework wraps it in a transaction that is still open while the test consumes the stream.
 *
 * <p><b>How to fix it:</b> collect inside the transaction, as {@link #fixed()} does, or make the
 * consuming code transactional.
 */
public class TxLazyReturnExample {

  @interface Transactional {}

  interface OrderRepository {
    Stream<String> streamAllOrders();
  }

  private final OrderRepository orders;

  public TxLazyReturnExample(OrderRepository orders) {
    this.orders = orders;
  }

  @Transactional
  public Stream<String> buggy() { // :: CK-TX-LAZY-RETURN
    return orders.streamAllOrders();
  }

  @Transactional
  public List<String> fixed() {
    try (Stream<String> all = orders.streamAllOrders()) {
      return all.toList();
    }
  }
}
