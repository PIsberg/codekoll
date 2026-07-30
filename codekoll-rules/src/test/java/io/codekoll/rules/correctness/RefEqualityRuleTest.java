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

  /**
   * Found in the wild: async-test-lib's SynchronizedOnLiteralDetector uses this to detect
   * interned (literal) strings. Reported as CK-REF-EQUALITY at ERROR; it is deliberate.
   */
  @Test
  void allowsInternIdentityIdiom() {
    RuleTestHarness.assertFixture(rule, "N5", """
        class N5 {
          boolean m(String s) {
            return s == s.intern();
          }
        }
        """);
  }

  /**
   * The same idiom through a cast, as in async-test-lib's BoxedPrimitiveLockDetector:
   * {@code obj instanceof String && obj == ((String) obj).intern()}.
   */
  @Test
  void allowsInternIdentityIdiomThroughCast() {
    RuleTestHarness.assertFixture(rule, "N6", """
        class N6 {
          boolean m(Object obj) {
            return obj instanceof String && obj == ((String) obj).intern();
          }
        }
        """);
  }

  /** A call named intern() on a different receiver is still a reference comparison. */
  @Test
  void flagsInternOfADifferentString() {
    RuleTestHarness.assertFixture(rule, "P4", """
        class P4 {
          boolean m(String a, String b) {
            return a == b.intern(); // :: CK-REF-EQUALITY
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
