package io.codekoll.rules.correctness;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class AssertSideEffectRuleTest {

  private final AssertSideEffectRule rule = new AssertSideEffectRule();

  @Test
  void flagsAssignmentInsideAssert() {
    RuleTestHarness.assertFixture(rule, "P1", """
        class P1 {
          void m(int limit) {
            int used = 0;
            assert (used = limit * 2) > 0; // :: CK-ASSERT-SIDE-EFFECT
          }
        }
        """);
  }

  @Test
  void flagsIncrementInsideAssert() {
    RuleTestHarness.assertFixture(rule, "P2", """
        class P2 {
          private int calls;
          void m() {
            assert calls++ < 10; // :: CK-ASSERT-SIDE-EFFECT
          }
        }
        """);
  }

  @Test
  void flagsCollectionMutationInsideAssert() {
    RuleTestHarness.assertFixture(rule, "P3", """
        import java.util.HashSet;
        import java.util.Set;
        class P3 {
          void register(Set<String> seen, String id) {
            assert seen.add(id); // :: CK-ASSERT-SIDE-EFFECT
          }
          void m() {
            register(new HashSet<>(), "a");
          }
        }
        """);
  }

  @Test
  void flagsIteratorAdvanceInsideAssert() {
    RuleTestHarness.assertFixture(rule, "P4", """
        import java.util.Iterator;
        class P4 {
          void m(Iterator<String> it) {
            assert it.next() != null; // :: CK-ASSERT-SIDE-EFFECT
          }
        }
        """);
  }

  @Test
  void flagsSideEffectInAssertDetailMessage() {
    RuleTestHarness.assertFixture(rule, "P5", """
        import java.util.List;
        class P5 {
          void m(List<String> log, boolean ok) {
            assert ok : log.remove(0); // :: CK-ASSERT-SIDE-EFFECT
          }
        }
        """);
  }

  @Test
  void flagsCompoundAssignmentInsideAssert() {
    RuleTestHarness.assertFixture(rule, "P6", """
        class P6 {
          private int total;
          void m(int delta) {
            assert (total += delta) >= 0; // :: CK-ASSERT-SIDE-EFFECT
          }
        }
        """);
  }

  @Test
  void allowsPureQueriesInsideAssert() {
    RuleTestHarness.assertFixture(rule, "N1", """
        import java.util.List;
        import java.util.Map;
        class N1 {
          void m(List<String> items, Map<String, String> index) {
            assert !items.isEmpty() && index.containsKey("k") && items.size() < 100;
          }
        }
        """);
  }

  @Test
  void allowsUnqualifiedCallWithNoVisibleReceiver() {
    RuleTestHarness.assertFixture(rule, "N2", """
        class N2 {
          private boolean valid(int x) {
            return x > 0;
          }
          void m(int x) {
            assert valid(x);
          }
        }
        """);
  }

  @Test
  void allowsMutatorNamedMethodOnUnknownReceiverType() {
    RuleTestHarness.assertFixture(rule, "N3", """
        class N3 {
          static final class Rules {
            boolean add(String s) {
              return s != null;
            }
          }
          void m(Rules rules) {
            assert rules.add("x");
          }
        }
        """);
  }

  @Test
  void allowsMutationOutsideAnyAssert() {
    RuleTestHarness.assertFixture(rule, "N4", """
        import java.util.Set;
        class N4 {
          void m(Set<String> seen, String id) {
            boolean added = seen.add(id);
            assert added;
          }
        }
        """);
  }

  @Test
  void allowsMutationInAMethodDeclaredAfterAnAssert() {
    RuleTestHarness.assertFixture(rule, "N5", """
        import java.util.Set;
        class N5 {
          void guard(boolean ok) {
            assert ok;
          }
          void mutate(Set<String> seen) {
            seen.add("x");
          }
        }
        """);
  }
}
