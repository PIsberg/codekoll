package io.codekoll.rules.correctness;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class IteratorDoubleNextRuleTest {

  private final IteratorDoubleNextRule rule = new IteratorDoubleNextRule();

  @Test
  void flagsTwoNextCallsInAWhileBody() {
    RuleTestHarness.assertFixture(rule, "P1", """
        import java.util.Iterator;
        class P1 {
          void m(Iterator<String> it) {
            while (it.hasNext()) {
              String key = it.next();
              String value = it.next(); // :: CK-ITERATOR-DOUBLE-NEXT
              System.out.println(key + "=" + value);
            }
          }
        }
        """);
  }

  @Test
  void flagsSecondNextInsideAnUnrelatedIf() {
    RuleTestHarness.assertFixture(rule, "P2", """
        import java.util.Iterator;
        class P2 {
          void m(Iterator<String> it, boolean verbose) {
            while (it.hasNext()) {
              String first = it.next();
              if (verbose) {
                System.out.println(it.next()); // :: CK-ITERATOR-DOUBLE-NEXT
              }
              System.out.println(first);
            }
          }
        }
        """);
  }

  @Test
  void flagsTwoNextCallsInAForLoopBody() {
    RuleTestHarness.assertFixture(rule, "P3", """
        import java.util.Iterator;
        import java.util.List;
        class P3 {
          void m(List<String> items) {
            for (Iterator<String> it = items.iterator(); it.hasNext(); ) {
              String a = it.next();
              String b = it.next(); // :: CK-ITERATOR-DOUBLE-NEXT
              System.out.println(a + b);
            }
          }
        }
        """);
  }

  @Test
  void flagsNextTwiceInOneExpression() {
    RuleTestHarness.assertFixture(rule, "P4", """
        import java.util.Iterator;
        class P4 {
          void m(Iterator<String> it) {
            while (it.hasNext()) {
              System.out.println(it.next() + "=" + it.next()); // :: CK-ITERATOR-DOUBLE-NEXT
            }
          }
        }
        """);
  }

  @Test
  void allowsASingleNextPerIteration() {
    RuleTestHarness.assertFixture(rule, "N1", """
        import java.util.Iterator;
        class N1 {
          void m(Iterator<String> it) {
            while (it.hasNext()) {
              String value = it.next();
              System.out.println(value + value);
            }
          }
        }
        """);
  }

  @Test
  void allowsASecondReadGuardedByItsOwnHasNext() {
    RuleTestHarness.assertFixture(rule, "N2", """
        import java.util.Iterator;
        class N2 {
          void m(Iterator<String> it) {
            while (it.hasNext()) {
              String key = it.next();
              if (it.hasNext()) {
                System.out.println(key + "=" + it.next());
              }
            }
          }
        }
        """);
  }

  @Test
  void allowsNextCallsInOppositeBranchesOfOneIf() {
    RuleTestHarness.assertFixture(rule, "N3", """
        import java.util.Iterator;
        class N3 {
          void m(Iterator<String> it, boolean upper) {
            while (it.hasNext()) {
              if (upper) {
                System.out.println(it.next());
              } else {
                System.out.println(it.next());
              }
            }
          }
        }
        """);
  }

  @Test
  void allowsTwoDifferentIterators() {
    RuleTestHarness.assertFixture(rule, "N4", """
        import java.util.Iterator;
        class N4 {
          void m(Iterator<String> keys, Iterator<String> values) {
            while (keys.hasNext()) {
              System.out.println(keys.next() + "=" + values.next());
            }
          }
        }
        """);
  }

  @Test
  void allowsANestedLoopDrainingTheSameIterator() {
    RuleTestHarness.assertFixture(rule, "N5", """
        import java.util.Iterator;
        class N5 {
          void m(Iterator<String> it) {
            while (it.hasNext()) {
              String head = it.next();
              while (it.hasNext()) {
                System.out.println(head + it.next());
              }
            }
          }
        }
        """);
  }

  @Test
  void allowsNextOutsideAnyHasNextGuardedLoop() {
    RuleTestHarness.assertFixture(rule, "N6", """
        import java.util.Iterator;
        class N6 {
          void m(Iterator<String> it) {
            String a = it.next();
            String b = it.next();
            System.out.println(a + b);
          }
        }
        """);
  }

  @Test
  void allowsSameNamedMethodsOnANonIterator() {
    RuleTestHarness.assertFixture(rule, "N7", """
        class N7 {
          static final class Cursor {
            boolean hasNext() {
              return false;
            }
            String next() {
              return "";
            }
          }
          void m(Cursor cursor) {
            while (cursor.hasNext()) {
              System.out.println(cursor.next() + cursor.next());
            }
          }
        }
        """);
  }
}
