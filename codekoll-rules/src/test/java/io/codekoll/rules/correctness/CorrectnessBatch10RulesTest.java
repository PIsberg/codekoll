package io.codekoll.rules.correctness;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class CorrectnessBatch10RulesTest {

  @Test
  void collectionSelfAddFlagged() {
    RuleTestHarness.assertFixture(new CollectionSelfAddRule(), "P1", """
        import java.util.List;
        class P1 {
          void m(List<Object> items) {
            items.add(items); // :: CK-COLLECTION-SELF-ADD
          }
        }
        """);
  }

  @Test
  void normalAddAllowed() {
    RuleTestHarness.assertFixture(new CollectionSelfAddRule(), "N1", """
        import java.util.List;
        class N1 {
          void m(List<Object> items, List<Object> other, Object x) {
            items.add(x);
            items.addAll(other);
          }
        }
        """);
  }

  @Test
  void infiniteRecursionFlagged() {
    RuleTestHarness.assertFixture(new InfiniteRecursionRule(), "P2", """
        class P2 {
          int size;
          int getSize() {
            return getSize(); // :: CK-INFINITE-RECURSION
          }
        }
        """);
  }

  @Test
  void terminatingAndDelegatingAllowed() {
    RuleTestHarness.assertFixture(new InfiniteRecursionRule(), "N2", """
        class N2 {
          int size;
          int getSize() {
            return size;
          }
          int factorial(int n) {
            if (n <= 1) {
              return 1;
            }
            return n * factorial(n - 1);
          }
        }
        """);
  }

  @Test
  void equalsOverloadFlagged() {
    RuleTestHarness.assertFixture(new EqualsOverloadRule(), "P3", """
        class P3 {
          int id;
          public boolean equals(P3 other) { // :: CK-EQUALS-OVERLOAD
            return other != null && id == other.id;
          }
        }
        """);
  }

  @Test
  void properOverrideAllowed() {
    RuleTestHarness.assertFixture(new EqualsOverloadRule(), "N3", """
        class N3 {
          int id;
          @Override
          public boolean equals(Object o) {
            return o instanceof N3 other && id == other.id;
          }
          @Override
          public int hashCode() {
            return id;
          }
        }
        """);
  }

  @Test
  void bigDecimalEqualsFlagged() {
    RuleTestHarness.assertFixture(new BigdecimalEqualsRule(), "P4", """
        import java.math.BigDecimal;
        class P4 {
          boolean m(BigDecimal a, BigDecimal b) {
            return a.equals(b); // :: CK-BIGDECIMAL-EQUALS
          }
        }
        """);
  }

  @Test
  void compareToAllowed() {
    RuleTestHarness.assertFixture(new BigdecimalEqualsRule(), "N4", """
        import java.math.BigDecimal;
        class N4 {
          boolean m(BigDecimal a, BigDecimal b, String s) {
            return a.compareTo(b) == 0 && s.equals("x");
          }
        }
        """);
  }

  @Test
  void formatMismatchFlagged() {
    RuleTestHarness.assertFixture(new FormatMismatchRule(), "P5", """
        class P5 {
          String m(String name, int count) {
            return String.format("%s has %d items and %d more", name, count); // :: CK-FORMAT-MISMATCH
          }
        }
        """);
  }

  @Test
  void matchedFormatAllowed() {
    RuleTestHarness.assertFixture(new FormatMismatchRule(), "N5", """
        class N5 {
          String m(String name, int count) {
            String a = String.format("%s has %d items (%d%%)", name, count, count);
            String b = "%s: %d".formatted(name, count);
            return a + b;
          }
        }
        """);
  }
}
