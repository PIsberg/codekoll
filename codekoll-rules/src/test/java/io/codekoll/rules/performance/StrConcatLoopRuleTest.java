package io.codekoll.rules.performance;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class StrConcatLoopRuleTest {

  private final StrConcatLoopRule rule = new StrConcatLoopRule();

  @Test
  void flagsPlusAssignInEnhancedFor() {
    RuleTestHarness.assertFixture(rule, "P1", """
        import java.util.List;
        class P1 {
          String m(List<String> records) {
            String report = "";
            for (String record : records) {
              report += record; // :: CK-STR-CONCAT-LOOP
            }
            return report;
          }
        }
        """);
  }

  @Test
  void flagsSelfPlusInWhile() {
    RuleTestHarness.assertFixture(rule, "P2", """
        class P2 {
          String m(int n) {
            String s = "";
            int i = 0;
            while (i < n) {
              s = s + i; // :: CK-STR-CONCAT-LOOP
              i++;
            }
            return s;
          }
        }
        """);
  }

  @Test
  void flagsConcatCallInFor() {
    RuleTestHarness.assertFixture(rule, "P3", """
        class P3 {
          String m(String[] parts) {
            String out = "";
            for (int i = 0; i < parts.length; i++) {
              out = out.concat(parts[i]); // :: CK-STR-CONCAT-LOOP
            }
            return out;
          }
        }
        """);
  }

  @Test
  void allowsStringBuilderInLoop() {
    RuleTestHarness.assertFixture(rule, "N1", """
        import java.util.List;
        class N1 {
          String m(List<String> records) {
            StringBuilder report = new StringBuilder();
            for (String record : records) {
              report.append(record);
            }
            return report.toString();
          }
        }
        """);
  }

  @Test
  void allowsFreshStringPerIteration() {
    RuleTestHarness.assertFixture(rule, "N2", """
        import java.util.List;
        class N2 {
          void m(List<String> records) {
            for (String record : records) {
              String line = "prefix";
              line += record;
              System.out.println(line);
            }
          }
        }
        """);
  }

  @Test
  void allowsConcatOutsideLoops() {
    RuleTestHarness.assertFixture(rule, "N3", """
        class N3 {
          String m(String a, String b) {
            String s = a;
            s += b;
            return s;
          }
        }
        """);
  }

  @Test
  void allowsIntAccumulatorInLoop() {
    RuleTestHarness.assertFixture(rule, "N4", """
        class N4 {
          int m(int[] values) {
            int sum = 0;
            for (int v : values) {
              sum += v;
            }
            return sum;
          }
        }
        """);
  }
}
