package io.codekoll.rules.numeric;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class NumericRulesTest {

  @Test
  void shiftOobFlagged() {
    RuleTestHarness.assertFixture(new ShiftOobRule(), "P1", """
        class P1 {
          long m(int bit) {
            int a = 1 << 32; // :: CK-SHIFT-OOB
            long b = 1L << 64; // :: CK-SHIFT-OOB
            return a + b + bit;
          }
        }
        """);
  }

  @Test
  void validShiftsAllowed() {
    RuleTestHarness.assertFixture(new ShiftOobRule(), "N1", """
        class N1 {
          long m(int n) {
            int a = 1 << 31;
            long b = 1L << 63;
            int c = 1 << n;
            return a + b + c;
          }
        }
        """);
  }

  @Test
  void divZeroFlagged() {
    RuleTestHarness.assertFixture(new DivZeroRule(), "P2", """
        class P2 {
          int m(int total) {
            return total / 0; // :: CK-DIV-ZERO
          }
        }
        """);
  }

  @Test
  void normalDivisionAndDoubleZeroAllowed() {
    RuleTestHarness.assertFixture(new DivZeroRule(), "N2", """
        class N2 {
          double m(int total, int count) {
            double inf = total / 0.0;
            return count == 0 ? inf : total / count;
          }
        }
        """);
  }

  @Test
  void compareSubtractFlagged() {
    RuleTestHarness.assertFixture(new CompareSubtractRule(), "P3", """
        class P3 implements Comparable<P3> {
          int priority;
          @Override
          public int compareTo(P3 other) {
            return priority - other.priority; // :: CK-COMPARE-SUBTRACT
          }
        }
        """);
  }

  @Test
  void integerCompareAndOtherSubtractionsAllowed() {
    RuleTestHarness.assertFixture(new CompareSubtractRule(), "N3", """
        class N3 implements Comparable<N3> {
          int priority;
          @Override
          public int compareTo(N3 other) {
            return Integer.compare(priority, other.priority);
          }
          int diff(N3 other) {
            return priority - other.priority;
          }
        }
        """);
  }

  @Test
  void intDivFloatFlagged() {
    RuleTestHarness.assertFixture(new IntDivFloatRule(), "P4", """
        class P4 {
          double m(int hits, int total) {
            double ratio = hits / total; // :: CK-INT-DIV-FLOAT
            return ratio;
          }
        }
        """);
  }

  @Test
  void widenedDivisionAllowed() {
    RuleTestHarness.assertFixture(new IntDivFloatRule(), "N4", """
        class N4 {
          double m(int hits, int total) {
            double a = (double) hits / total;
            double b = hits * 1.0 / total;
            int c = hits / total;
            return a + b + c;
          }
        }
        """);
  }

  @Test
  void absOverflowFlagged() {
    RuleTestHarness.assertFixture(new AbsOverflowRule(), "P5", """
        class P5 {
          int m(String key, int buckets) {
            return Math.abs(key.hashCode()) % buckets; // :: CK-ABS-OVERFLOW
          }
        }
        """);
  }

  @Test
  void floorModAndPlainAbsAllowed() {
    RuleTestHarness.assertFixture(new AbsOverflowRule(), "N5", """
        class N5 {
          int m(String key, int buckets, int delta) {
            int idx = Math.floorMod(key.hashCode(), buckets);
            return idx + Math.abs(delta);
          }
        }
        """);
  }
}
