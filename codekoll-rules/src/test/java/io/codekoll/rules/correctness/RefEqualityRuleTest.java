package io.codekoll.rules.correctness;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class RefEqualityRuleTest {

  private final RefEqualityRule rule = new RefEqualityRule();

  @Test
  void flagsStringComparison() {
    RuleTestHarness.assertFixture(rule, "P1", """
        class P1 {
          boolean m(String status) {
            return status == "ACTIVE"; // :: CK-REF-EQUALITY
          }
        }
        """);
  }

  @Test
  void flagsBoxedComparison() {
    RuleTestHarness.assertFixture(rule, "P2", """
        class P2 {
          boolean m(Integer a, Integer b) {
            return a != b; // :: CK-REF-EQUALITY
          }
        }
        """);
  }

  @Test
  void allowsNullChecks() {
    RuleTestHarness.assertFixture(rule, "N1", """
        class N1 {
          boolean m(String s) {
            return s == null || null != s;
          }
        }
        """);
  }

  @Test
  void allowsBoxedVsPrimitive() {
    RuleTestHarness.assertFixture(rule, "N2", """
        class N2 {
          boolean m(Integer a, int b) {
            return a == b;
          }
        }
        """);
  }

  @Test
  void allowsEnumComparison() {
    RuleTestHarness.assertFixture(rule, "N3", """
        class N3 {
          enum Color { RED, GREEN }
          boolean m(Color a, Color b) {
            return a == b;
          }
        }
        """);
  }

  @Test
  void allowsThisIdentityFastPathInEquals() {
    RuleTestHarness.assertFixture(rule, "N4", """
        class N4 {
          private final String name = "";
          @Override
          public boolean equals(Object obj) {
            if (this == obj) {
              return true;
            }
            return obj instanceof N4 other && name.equals(other.name);
          }
          @Override
          public int hashCode() {
            return name.hashCode();
          }
        }
        """);
  }

  @Test
  void flagsStringComparisonEvenInsideEqualsWhenNotThis() {
    RuleTestHarness.assertFixture(rule, "P3", """
        class P3 {
          private final String name = "";
          @Override
          public boolean equals(Object obj) {
            if (!(obj instanceof P3 other)) {
              return false;
            }
            return name == other.name; // :: CK-REF-EQUALITY
          }
          @Override
          public int hashCode() {
            return name.hashCode();
          }
        }
        """);
  }
}
