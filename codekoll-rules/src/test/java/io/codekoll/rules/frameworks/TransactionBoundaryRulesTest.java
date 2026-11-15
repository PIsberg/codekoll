package io.codekoll.rules.frameworks;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

/**
 * CK-TX-LAZY-RETURN, CK-ASYNC-THREAD-CONTEXT and CK-TX-READONLY-WRITE (issue #121). The
 * annotations and holders are local stand-ins: the pack matches by simple name when the framework
 * is not on the classpath.
 */
class TransactionBoundaryRulesTest {

  @Test
  void lazyResultFromTransactionalMethodFlagged() {
    RuleTestHarness.assertFixture(new TxLazyReturnRule(), "P1", """
        import java.io.OutputStream;
        import java.util.stream.IntStream;
        import java.util.stream.Stream;
        class P1 {
          @interface Transactional {}
          interface StreamingResponseBody {
            void writeTo(OutputStream out);
          }
          interface OrderRepository {
            Stream<String> streamAll();
            IntStream ids();
          }
          private final OrderRepository orders;
          P1(OrderRepository orders) {
            this.orders = orders;
          }
          @Transactional
          public Stream<String> all() { // :: CK-TX-LAZY-RETURN
            return orders.streamAll();
          }
          @Transactional
          public IntStream ids() { // :: CK-TX-LAZY-RETURN
            return orders.ids().filter(id -> id > 0);
          }
          @Transactional
          public StreamingResponseBody export() { // :: CK-TX-LAZY-RETURN
            return out -> orders.streamAll().forEach(line -> {});
          }
        }
        """);
  }

  @Test
  void eagerOrInMemoryResultsAllowed() {
    RuleTestHarness.assertFixture(new TxLazyReturnRule(), "N1", """
        import java.util.Arrays;
        import java.util.List;
        import java.util.stream.Stream;
        class N1 {
          @interface Transactional {}
          interface OrderRepository {
            Stream<String> streamAll();
          }
          private final OrderRepository orders;
          private final List<String> cached = List.of("a");
          N1(OrderRepository orders) {
            this.orders = orders;
          }
          @Transactional
          public List<String> eager() {
            return orders.streamAll().toList();
          }
          @Transactional
          public Stream<String> inMemory(String[] names) {
            if (names.length == 0) {
              return cached.stream();
            }
            return names.length == 1 ? Stream.of(names[0]) : Arrays.stream(names);
          }
          public Stream<String> notTransactional() {
            return orders.streamAll();
          }
        }
        """);
  }

  @Test
  void threadContextReadOnAsyncThreadFlagged() {
    RuleTestHarness.assertFixture(new AsyncThreadContextRule(), "P2", """
        import java.util.Locale;
        import java.util.concurrent.CompletableFuture;
        class P2 {
          @interface Async {}
          static class SecurityContextHolder {
            static Object getContext() {
              return null;
            }
          }
          static class MDC {
            static String get(String key) {
              return key;
            }
          }
          static class LocaleContextHolder {
            static Locale getLocale() {
              return Locale.ROOT;
            }
          }
          @Async
          public void audit() {
            Object who = SecurityContextHolder.getContext(); // :: CK-ASYNC-THREAD-CONTEXT
            System.out.println(who);
          }
          CompletableFuture<String> traced() {
            return CompletableFuture.supplyAsync(() -> MDC.get("traceId")); // :: CK-ASYNC-THREAD-CONTEXT
          }
          CompletableFuture<String> localized(CompletableFuture<String> f) {
            return f.thenApplyAsync(s -> s + LocaleContextHolder.getLocale()); // :: CK-ASYNC-THREAD-CONTEXT
          }
        }
        """);
  }

  @Test
  void contextCapturedBeforeHandOffAllowed() {
    RuleTestHarness.assertFixture(new AsyncThreadContextRule(), "N2", """
        import java.util.concurrent.CompletableFuture;
        class N2 {
          @interface Async {}
          static class MDC {
            static String get(String key) {
              return key;
            }
            static void put(String key, String value) {}
          }
          CompletableFuture<String> captured() {
            String traceId = MDC.get("traceId");
            return CompletableFuture.supplyAsync(() -> {
              MDC.put("traceId", traceId);
              return traceId;
            });
          }
          String sameThread() {
            return MDC.get("traceId");
          }
          CompletableFuture<String> syncStage(CompletableFuture<String> f) {
            return f.thenApply(s -> s + MDC.get("traceId"));
          }
          @Async
          public void writesOnly(String traceId) {
            MDC.put("traceId", traceId);
          }
        }
        """);
  }

  @Test
  void writeInReadOnlyTransactionFlagged() {
    RuleTestHarness.assertFixture(new TxReadonlyWriteRule(), "P3", """
        class P3 {
          @interface Transactional {
            boolean readOnly() default false;
          }
          @interface Modifying {}
          record Order(long id) {}
          interface OrderRepository {
            Order save(Order order);
            void deleteById(long id);
            @Modifying
            int purgeExpired();
          }
          interface EntityManager {
            void persist(Object entity);
          }
          private final OrderRepository orders;
          private final EntityManager em;
          P3(OrderRepository orders, EntityManager em) {
            this.orders = orders;
            this.em = em;
          }
          @Transactional(readOnly = true)
          public Order touch(Order order) {
            em.persist(order); // :: CK-TX-READONLY-WRITE
            orders.deleteById(order.id()); // :: CK-TX-READONLY-WRITE
            return orders.save(order); // :: CK-TX-READONLY-WRITE
          }
          @Transactional(readOnly = true)
          public int cleanup() {
            return orders.purgeExpired(); // :: CK-TX-READONLY-WRITE
          }
        }
        """);
  }

  @Test
  void readOnlyClassOverriddenOrNonRepositoryCallsAllowed() {
    RuleTestHarness.assertFixture(new TxReadonlyWriteRule(), "N3", """
        import java.util.ArrayList;
        import java.util.List;
        @N3.Transactional(readOnly = true)
        class N3 {
          @interface Transactional {
            boolean readOnly() default false;
          }
          record Order(long id) {}
          interface OrderRepository {
            Order save(Order order);
            Order findById(long id);
          }
          private final OrderRepository orders;
          N3(OrderRepository orders) {
            this.orders = orders;
          }
          public Order find(long id) {
            List<Order> seen = new ArrayList<>();
            Order order = orders.findById(id);
            seen.add(order);
            seen.remove(order);
            return order;
          }
          @Transactional
          public Order store(Order order) {
            return orders.save(order);
          }
          @Transactional(readOnly = false)
          public Order storeExplicitly(Order order) {
            return orders.save(order);
          }
        }
        """);
  }

  @Test
  void readOnlyClassAnnotationAppliesToMethods() {
    RuleTestHarness.assertFixture(new TxReadonlyWriteRule(), "P4", """
        @P4.Transactional(readOnly = true)
        class P4 {
          @interface Transactional {
            boolean readOnly() default false;
          }
          record Order(long id) {}
          interface OrderRepository {
            Order save(Order order);
          }
          private final OrderRepository orders;
          P4(OrderRepository orders) {
            this.orders = orders;
          }
          public Order store(Order order) {
            return orders.save(order); // :: CK-TX-READONLY-WRITE
          }
        }
        """);
  }
}
