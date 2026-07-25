package io.codekoll.rules.correctness;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class CorrectnessBatch9RulesTest {

  @Test
  void equalsIncompatibleFlagged() {
    RuleTestHarness.assertFixture(new EqualsIncompatibleRule(), "P1", """
        class P1 {
          boolean m(Long id, Integer other) {
            return id.equals(other); // :: CK-EQUALS-INCOMPATIBLE
          }
        }
        """);
  }

  @Test
  void compatibleEqualsAllowed() {
    RuleTestHarness.assertFixture(new EqualsIncompatibleRule(), "N1", """
        class N1 {
          boolean m(String a, String b, Object o, CharSequence cs) {
            return a.equals(b) || a.equals(o) || a.equals(cs);
          }
        }
        """);
  }

  @Test
  void toStringArrayFlagged() {
    RuleTestHarness.assertFixture(new ToStringArrayRule(), "P2", """
        class P2 {
          void m(String[] names) {
            System.out.println("names: " + names); // :: CK-TOSTRING-ARRAY
            System.out.println(names); // :: CK-TOSTRING-ARRAY
          }
        }
        """);
  }

  @Test
  void arraysToStringAndCharArrayAllowed() {
    RuleTestHarness.assertFixture(new ToStringArrayRule(), "N2", """
        import java.util.Arrays;
        class N2 {
          void m(String[] names, char[] chars) {
            System.out.println("names: " + Arrays.toString(names));
            System.out.println(chars);
          }
        }
        """);
  }

  @Test
  void urlEqualsFlagged() {
    RuleTestHarness.assertFixture(new UrlEqualsRule(), "P3", """
        import java.net.URL;
        class P3 {
          boolean m(URL a, URL b) {
            return a.equals(b); // :: CK-URL-EQUALS
          }
        }
        """);
  }

  @Test
  void uriEqualsAllowed() {
    RuleTestHarness.assertFixture(new UrlEqualsRule(), "N3", """
        import java.net.URI;
        class N3 {
          boolean m(URI a, URI b) {
            return a.equals(b);
          }
        }
        """);
  }

  @Test
  void switchFallthroughFlagged() {
    RuleTestHarness.assertFixture(new SwitchFallthroughRule(), "P4", """
        class P4 {
          int m(int code) {
            int result = 0;
            switch (code) {
              case 1: // :: CK-SWITCH-FALLTHROUGH
                result = 10;
              case 2:
                result += 20;
                break;
              default:
                result = -1;
            }
            return result;
          }
        }
        """);
  }

  @Test
  void breakingAndArrowSwitchAllowed() {
    RuleTestHarness.assertFixture(new SwitchFallthroughRule(), "N4", """
        class N4 {
          int m(int code) {
            switch (code) {
              case 1:
                return 10;
              case 2:
                return 20;
              default:
                return -1;
            }
          }
          int arrow(int code) {
            return switch (code) {
              case 1 -> 10;
              default -> -1;
            };
          }
        }
        """);
  }
}
