package io.codekoll.rules.correctness;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class SimpleCorrectnessRulesTest {

  @Test
  void equalsNullArgFlagged() {
    RuleTestHarness.assertFixture(new EqualsNullArgRule(), "P1", """
        class P1 {
          boolean m(String s) {
            return s.equals(null); // :: CK-EQUALS-NULL-ARG
          }
        }
        """);
  }

  @Test
  void equalsRealArgAllowed() {
    RuleTestHarness.assertFixture(new EqualsNullArgRule(), "N1", """
        class N1 {
          boolean m(String s, String t) {
            return s.equals(t) && s == null;
          }
        }
        """);
  }

  @Test
  void exceptionNotThrownFlagged() {
    RuleTestHarness.assertFixture(new ExceptionNotThrownRule(), "P2", """
        class P2 {
          void m(int age) {
            if (age < 0) {
              new IllegalArgumentException("negative age"); // :: CK-EXCEPTION-NOT-THROWN
            }
          }
        }
        """);
  }

  @Test
  void thrownAndStoredExceptionsAllowed() {
    RuleTestHarness.assertFixture(new ExceptionNotThrownRule(), "N2", """
        class N2 {
          void m(int age) {
            if (age < 0) {
              throw new IllegalArgumentException("negative age");
            }
            RuntimeException pending = new RuntimeException("kept");
            System.out.println(pending.getMessage());
          }
        }
        """);
  }

  @Test
  void optionalNullFlagged() {
    RuleTestHarness.assertFixture(new OptionalNullRule(), "P3", """
        import java.util.Optional;
        class P3 {
          Optional<String> m(boolean found) {
            if (found) {
              return Optional.of("x");
            }
            return null; // :: CK-OPTIONAL-NULL
          }
        }
        """);
  }

  @Test
  void optionalEmptyAndPlainNullReturnsAllowed() {
    RuleTestHarness.assertFixture(new OptionalNullRule(), "N3", """
        import java.util.Optional;
        class N3 {
          Optional<String> m(boolean found) {
            return found ? Optional.of("x") : Optional.empty();
          }
          String plain() {
            return null;
          }
        }
        """);
  }

  @Test
  void sbCharCtorFlagged() {
    RuleTestHarness.assertFixture(new SbCharCtorRule(), "P4", """
        class P4 {
          String m() {
            return new StringBuilder('a').toString(); // :: CK-SB-CHAR-CTOR
          }
        }
        """);
  }

  @Test
  void sbStringAndCapacityCtorsAllowed() {
    RuleTestHarness.assertFixture(new SbCharCtorRule(), "N4", """
        class N4 {
          String m() {
            return new StringBuilder("a").append(new StringBuilder(16)).toString();
          }
        }
        """);
  }

  @Test
  void nanCompareFlagged() {
    RuleTestHarness.assertFixture(new NanCompareRule(), "P5", """
        class P5 {
          boolean m(double d) {
            return d == Double.NaN; // :: CK-NAN-COMPARE
          }
        }
        """);
  }

  @Test
  void isNanAllowed() {
    RuleTestHarness.assertFixture(new NanCompareRule(), "N5", """
        class N5 {
          boolean m(double d) {
            return Double.isNaN(d) || d == Double.MAX_VALUE;
          }
        }
        """);
  }

  @Test
  void arrayObjectMethodsFlagged() {
    RuleTestHarness.assertFixture(new ArrayObjectMethodsRule(), "P6", """
        class P6 {
          void m(int[] a, int[] b) {
            boolean eq = a.equals(b); // :: CK-ARRAY-OBJECT-METHODS
            int h = a.hashCode(); // :: CK-ARRAY-OBJECT-METHODS
            String s = b.toString(); // :: CK-ARRAY-OBJECT-METHODS
            System.out.println(eq + s + h);
          }
        }
        """);
  }

  @Test
  void arraysUtilityAndObjectReceiversAllowed() {
    RuleTestHarness.assertFixture(new ArrayObjectMethodsRule(), "N6", """
        import java.util.Arrays;
        class N6 {
          void m(int[] a, int[] b, String s) {
            boolean eq = Arrays.equals(a, b) && s.equals("x");
            String text = Arrays.toString(a) + s.toString();
            System.out.println(eq + text);
          }
        }
        """);
  }
}
