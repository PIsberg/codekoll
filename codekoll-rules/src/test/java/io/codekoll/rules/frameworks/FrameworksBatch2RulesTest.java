package io.codekoll.rules.frameworks;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class FrameworksBatch2RulesTest {

  @Test
  void proxySelfInvokeFlagged() {
    RuleTestHarness.assertFixture(new ProxySelfInvokeRule(), "P1", """
        class P1 {
          @interface Transactional {}
          void outer() {
            inner(); // :: CK-PROXY-SELF-INVOKE
          }
          @Transactional
          void inner() {
          }
        }
        """);
  }

  @Test
  void externalCallAndNonProxiedAllowed() {
    RuleTestHarness.assertFixture(new ProxySelfInvokeRule(), "N1", """
        class N1 {
          @interface Transactional {}
          private N1 self;
          void outer() {
            self.inner();
            helper();
          }
          @Transactional
          void inner() {
          }
          void helper() {
          }
        }
        """);
  }

  @Test
  void finalEntityFlagged() {
    RuleTestHarness.assertFixture(new EntityContractRule(), "P2", """
        class P2 {
          @interface Entity {}
          @Entity // :: CK-ENTITY-CONTRACT
          static final class Account {
            int id;
          }
        }
        """);
  }

  @Test
  void entityWithoutNoArgCtorFlagged() {
    RuleTestHarness.assertFixture(new EntityContractRule(), "P3", """
        class P3 {
          @interface Entity {}
          @Entity // :: CK-ENTITY-CONTRACT
          static class Account {
            final int id;
            Account(int id) {
              this.id = id;
            }
          }
        }
        """);
  }

  @Test
  void wellFormedEntityAllowed() {
    RuleTestHarness.assertFixture(new EntityContractRule(), "N2", """
        class N2 {
          @interface Entity {}
          @Entity
          static class Account {
            int id;
            protected Account() {
            }
            Account(int id) {
              this.id = id;
            }
          }
          @Entity
          static class Simple {
            int id;
          }
        }
        """);
  }

  @Test
  void logExceptionLostFlagged() {
    RuleTestHarness.assertFixture(new LogExceptionLostRule(), "P4", """
        import org.slf4j.Logger;
        class P4 {
          void m(Logger log) {
            try {
              System.gc();
            } catch (RuntimeException e) {
              log.error("failed: " + e.getMessage()); // :: CK-LOG-EXCEPTION-LOST
            }
          }
        }
        """);
  }

  @Test
  void exceptionAsArgumentAllowed() {
    RuleTestHarness.assertFixture(new LogExceptionLostRule(), "N3", """
        import org.slf4j.Logger;
        class N3 {
          void m(Logger log) {
            try {
              System.gc();
            } catch (RuntimeException e) {
              log.error("operation failed", e);
            }
          }
        }
        """);
  }
}
