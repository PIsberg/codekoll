package io.codekoll.rules.frameworks;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class FrameworksRulesTest {

  @Test
  void proxyAnnotationOnPrivateAndFinalFlagged() {
    RuleTestHarness.assertFixture(new ProxyAnnotationInvisibleRule(), "P1", """
        class P1 {
          @interface Transactional {}
          @interface Async {}
          @Transactional // :: CK-PROXY-ANNOTATION-INVISIBLE
          private void save() {
          }
          @Async // :: CK-PROXY-ANNOTATION-INVISIBLE
          public final void notifyUser() {
          }
        }
        """);
  }

  @Test
  void publicOverridableProxyMethodsAllowed() {
    RuleTestHarness.assertFixture(new ProxyAnnotationInvisibleRule(), "N1", """
        class N1 {
          @interface Transactional {}
          @Transactional
          public void save() {
          }
          private void helper() {
          }
        }
        """);
  }

  @Test
  void staticInjectionFlagged() {
    RuleTestHarness.assertFixture(new InjectStaticRule(), "P2", """
        class P2 {
          @interface Autowired {}
          @interface Value {}
          @Autowired // :: CK-INJECT-STATIC
          private static P2 instance;
          @Value // :: CK-INJECT-STATIC
          static String configUrl;
        }
        """);
  }

  @Test
  void instanceInjectionAllowed() {
    RuleTestHarness.assertFixture(new InjectStaticRule(), "N2", """
        class N2 {
          @interface Autowired {}
          @Autowired
          private N2 collaborator;
          private static String plainStatic;
          Object use() {
            return collaborator + plainStatic;
          }
        }
        """);
  }

  @Test
  void invisibleTestsFlagged() {
    RuleTestHarness.assertFixture(new TestInvisibleRule(), "P3", """
        class P3 {
          @interface Test {}
          @Test // :: CK-TEST-INVISIBLE
          private void hiddenTest() {
          }
          @Test // :: CK-TEST-INVISIBLE
          static void staticTest() {
          }
          @Test // :: CK-TEST-INVISIBLE
          boolean returningTest() {
            return true;
          }
        }
        """);
  }

  @Test
  void discoverableTestsAllowed() {
    RuleTestHarness.assertFixture(new TestInvisibleRule(), "N3", """
        class N3 {
          @interface Test {}
          @Test
          void plainTest() {
          }
          @Test
          public void publicTest() {
          }
          private void helper() {
          }
        }
        """);
  }

  @Test
  void slf4jPlaceholderMismatchFlagged() {
    RuleTestHarness.assertFixture(new Slf4jPlaceholderRule(), "P4", """
        import org.slf4j.Logger;
        class P4 {
          void m(Logger log, String orderId, long amount) {
            log.info("order {} amount {}", orderId); // :: CK-SLF4J-PLACEHOLDER
            log.warn("order {}", orderId, amount); // :: CK-SLF4J-PLACEHOLDER
          }
        }
        """);
  }

  @Test
  void matchedPlaceholdersAndThrowableConventionAllowed() {
    RuleTestHarness.assertFixture(new Slf4jPlaceholderRule(), "N4", """
        import org.slf4j.Logger;
        class N4 {
          void m(Logger log, String orderId, Exception e) {
            log.info("order {} processed", orderId);
            log.error("order {} failed", orderId, e);
            log.error("failure", e);
            log.debug("no args at all");
          }
        }
        """);
  }
}
