package examples.frameworks;

/**
 * Example for rule {@code CK-TX-READONLY-WRITE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(long)} is a read-only lookup that also records the
 * customer's last-seen time, in a {@code @Transactional(readOnly = true)} method. (The annotation
 * and repository are stand-ins for Spring's and Spring Data's; codekoll matches by name.)
 *
 * <p><b>What happens at runtime:</b> Spring runs read-only transactions with Hibernate's flush
 * disabled, so the {@code save} is never written: the last-seen time silently never changes.
 * With some drivers the connection is also marked read-only and the save fails instead, so the
 * same code can lose data on one database and throw on another.
 *
 * <p><b>How to fix it:</b> do the write in a read-write transaction, as {@link #fixed(long)} does.
 */
public class TxReadonlyWriteExample {

  @interface Transactional {
    boolean readOnly() default false;
  }

  record Customer(long id, long lastSeen) {}

  interface CustomerRepository {
    Customer findById(long id);

    Customer save(Customer customer);
  }

  private final CustomerRepository customers;

  public TxReadonlyWriteExample(CustomerRepository customers) {
    this.customers = customers;
  }

  @Transactional(readOnly = true)
  public Customer buggy(long id) {
    Customer customer = customers.findById(id);
    return customers.save(new Customer(customer.id(), System.currentTimeMillis())); // :: CK-TX-READONLY-WRITE
  }

  @Transactional
  public Customer fixed(long id) {
    Customer customer = customers.findById(id);
    return customers.save(new Customer(customer.id(), System.currentTimeMillis()));
  }
}
