package io.codekoll.rules.correctness;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class SelfCompareRuleTest {

  private final SelfCompareRule rule = new SelfCompareRule();

  @Test
  void flagsSelfEqualityAndSelfEquals() {
    RuleTestHarness.assertFixture(rule, "P1", """
        class P1 {
          boolean m(int x, String s) {
            boolean a = x == x; // :: CK-SELF-COMPARE
            boolean b = s.equals(s); // :: CK-SELF-COMPARE
            return a || b;
          }
        }
        """);
  }

  @Test
  void flagsSelfCompareOnFieldSelect() {
    RuleTestHarness.assertFixture(rule, "P2", """
        class P2 {
          int value;
          boolean m(P2 a) {
            return a.value >= a.value; // :: CK-SELF-COMPARE
          }
        }
        """);
  }

  @Test
  void allowsNanIdiomOnDouble() {
    RuleTestHarness.assertFixture(rule, "N1", """
        class N1 {
          boolean m(double d) {
            return d != d;
          }
        }
        """);
  }

  @Test
  void allowsDifferentOperands() {
    RuleTestHarness.assertFixture(rule, "N2", """
        class N2 {
          int value;
          boolean m(N2 a, N2 b, String s, String t) {
            return a.value == b.value && s.equals(t);
          }
        }
        """);
  }

  @Test
  void allowsMethodCallOperands() {
    RuleTestHarness.assertFixture(rule, "N3", """
        class N3 {
          boolean m(java.util.Random r) {
            return r.nextInt() == r.nextInt();
          }
        }
        """);
  }
}
