package io.codekoll.rules.apimisuse;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class PrimitiveArrayVarargsRuleTest {

  private final PrimitiveArrayVarargsRule rule = new PrimitiveArrayVarargsRule();

  @Test
  void flagsIntArrayPassedToArraysAsList() {
    RuleTestHarness.assertFixture(rule, "P1", """
        import java.util.Arrays;
        class P1 {
          int m(int[] ids) {
            var boxed = Arrays.asList(ids); // :: CK-PRIMITIVE-ARRAY-VARARGS
            return boxed.size();
          }
        }
        """);
  }

  @Test
  void flagsIntArrayLiteralPassedToArraysAsList() {
    RuleTestHarness.assertFixture(rule, "P2", """
        import java.util.Arrays;
        class P2 {
          int m() {
            return Arrays.asList(new int[] {1, 2, 3}).size(); // :: CK-PRIMITIVE-ARRAY-VARARGS
          }
        }
        """);
  }

  @Test
  void flagsLongArrayPassedToStreamOf() {
    RuleTestHarness.assertFixture(rule, "P3", """
        import java.util.stream.Stream;
        class P3 {
          long m(long[] values) {
            return Stream.of(values).count(); // :: CK-PRIMITIVE-ARRAY-VARARGS
          }
        }
        """);
  }

  @Test
  void flagsByteArrayPassedToArraysAsList() {
    RuleTestHarness.assertFixture(rule, "P4", """
        import java.util.Arrays;
        class P4 {
          int m(byte[] payload) {
            return Arrays.asList(payload).size(); // :: CK-PRIMITIVE-ARRAY-VARARGS
          }
        }
        """);
  }

  @Test
  void allowsReferenceArrayWhichSpreadsCorrectly() {
    RuleTestHarness.assertFixture(rule, "N1", """
        import java.util.Arrays;
        import java.util.List;
        class N1 {
          int m(String[] names, Integer[] ids) {
            List<String> a = Arrays.asList(names);
            List<Integer> b = Arrays.asList(ids);
            return a.size() + b.size();
          }
        }
        """);
  }

  @Test
  void allowsDeliberateListOfArrays() {
    RuleTestHarness.assertFixture(rule, "N2", """
        import java.util.Arrays;
        import java.util.List;
        class N2 {
          int m(int[] row) {
            List<int[]> rows = Arrays.asList(row);
            return rows.size();
          }
        }
        """);
  }

  @Test
  void allowsTheBoxingIdiom() {
    RuleTestHarness.assertFixture(rule, "N3", """
        import java.util.Arrays;
        import java.util.List;
        class N3 {
          List<Integer> m(int[] ids) {
            return Arrays.stream(ids).boxed().toList();
          }
        }
        """);
  }

  @Test
  void allowsMultipleArgumentsWhichAreRealVarargs() {
    RuleTestHarness.assertFixture(rule, "N4", """
        import java.util.Arrays;
        import java.util.List;
        class N4 {
          List<int[]> m(int[] a, int[] b) {
            return Arrays.asList(a, b);
          }
        }
        """);
  }

  @Test
  void allowsSameNamedFactoryOnAnUnrelatedType() {
    RuleTestHarness.assertFixture(rule, "N5", """
        class N5 {
          static final class Row {
            static Row of(int[] cells) {
              return new Row();
            }
          }
          Row m(int[] cells) {
            return Row.of(cells);
          }
        }
        """);
  }
}
