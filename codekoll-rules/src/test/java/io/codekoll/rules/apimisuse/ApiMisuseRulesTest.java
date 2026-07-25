package io.codekoll.rules.apimisuse;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class ApiMisuseRulesTest {

  @Test
  void toArrayCastFlagged() {
    RuleTestHarness.assertFixture(new ToArrayCastRule(), "P1", """
        import java.util.List;
        class P1 {
          String[] m(List<String> names) {
            return (String[]) names.toArray(); // :: CK-TOARRAY-CAST
          }
        }
        """);
  }

  @Test
  void typedToArrayAndObjectCastAllowed() {
    RuleTestHarness.assertFixture(new ToArrayCastRule(), "N1", """
        import java.util.List;
        class N1 {
          Object[] m(List<String> names) {
            String[] typed = names.toArray(new String[0]);
            String[] byRef = names.toArray(String[]::new);
            Object[] plain = names.toArray();
            return typed.length > 0 ? typed : byRef.length > 0 ? byRef : plain;
          }
        }
        """);
  }

  @Test
  void regexMetaLiteralFlagged() {
    RuleTestHarness.assertFixture(new RegexMetaLiteralRule(), "P2", """
        class P2 {
          String[] m(String filename) {
            return filename.split("."); // :: CK-REGEX-META-LITERAL
          }
          String n(String path) {
            return path.replaceAll("$", "!"); // :: CK-REGEX-META-LITERAL
          }
        }
        """);
  }

  @Test
  void escapedAndPlainSeparatorsAllowed() {
    RuleTestHarness.assertFixture(new RegexMetaLiteralRule(), "N2", """
        class N2 {
          Object[] m(String filename) {
            return new Object[] {
              filename.split("\\\\."),
              filename.split(","),
              filename.replace(".", "-"),
            };
          }
        }
        """);
  }

  @Test
  void removeIntOnIntegerListFlagged() {
    RuleTestHarness.assertFixture(new RemoveIntAmbiguousRule(), "P3", """
        import java.util.List;
        class P3 {
          void m(List<Integer> scores, int value) {
            scores.remove(value); // :: CK-REMOVE-INT-AMBIGUOUS
          }
        }
        """);
  }

  @Test
  void boxedRemoveAndOtherListsAllowed() {
    RuleTestHarness.assertFixture(new RemoveIntAmbiguousRule(), "N3", """
        import java.util.List;
        class N3 {
          void m(List<Integer> scores, List<String> names, int idx) {
            scores.remove(Integer.valueOf(5));
            names.remove(idx);
          }
        }
        """);
  }

  @Test
  void twoArgToMapFlagged() {
    RuleTestHarness.assertFixture(new ToMapDuplicatesRule(), "P4", """
        import java.util.List;
        import java.util.Map;
        import java.util.function.Function;
        import java.util.stream.Collectors;
        class P4 {
          Map<Integer, String> m(List<String> names) {
            return names.stream()
                .collect(Collectors.toMap(String::length, Function.identity())); // :: CK-TOMAP-DUPLICATES
          }
        }
        """);
  }

  @Test
  void mergedToMapAllowed() {
    RuleTestHarness.assertFixture(new ToMapDuplicatesRule(), "N4", """
        import java.util.List;
        import java.util.Map;
        import java.util.function.Function;
        import java.util.stream.Collectors;
        class N4 {
          Map<Integer, String> m(List<String> names) {
            return names.stream()
                .collect(Collectors.toMap(String::length, Function.identity(), (a, b) -> a));
          }
        }
        """);
  }
}
