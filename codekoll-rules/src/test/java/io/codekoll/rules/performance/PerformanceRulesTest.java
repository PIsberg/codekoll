package io.codekoll.rules.performance;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class PerformanceRulesTest {

  /**
   * Found in the wild: vibetags' RoleConfig and async-test-lib's AgentOptions both iterate
   * {@code x.split(...)}. The header runs once, so the regex is compiled once.
   */
  @Test
  void allowsSplitInTheForEachHeader() {
    RuleTestHarness.assertFixture(new RegexInLoopRule(), "N10", """
        class N10 {
          int m(String args) {
            int n = 0;
            for (String token : args.split("[,;]")) {
              n += token.length();
            }
            for (String line : args.split("\\r\\n|\\r|\\n", -1)) {
              n += line.length();
            }
            return n;
          }
        }
        """);
  }

  /** The same call one level in, inside an outer loop, is once per outer iteration. */
  @Test
  void flagsSplitInTheHeaderOfANestedLoop() {
    RuleTestHarness.assertFixture(new RegexInLoopRule(), "P10", """
        import java.util.List;
        class P10 {
          int m(List<String> rows) {
            int n = 0;
            for (String row : rows) {
              for (String cell : row.split("[,;]")) { // :: CK-REGEX-IN-LOOP
                n += cell.length();
              }
            }
            return n;
          }
        }
        """);
  }

  @Test
  void regexInLoopFlagged() {
    RuleTestHarness.assertFixture(new RegexInLoopRule(), "P1", """
        import java.util.List;
        import java.util.regex.Pattern;
        class P1 {
          int m(List<String> lines) {
            int hits = 0;
            for (String line : lines) {
              if (Pattern.compile("a+b").matcher(line).find()) { // :: CK-REGEX-IN-LOOP
                hits++;
              }
              if (line.matches("x.*y")) { // :: CK-REGEX-IN-LOOP
                hits++;
              }
            }
            return hits;
          }
        }
        """);
  }

  @Test
  void hoistedPatternAndFastPathSplitAllowed() {
    RuleTestHarness.assertFixture(new RegexInLoopRule(), "N1", """
        import java.util.List;
        import java.util.regex.Pattern;
        class N1 {
          private static final Pattern WORD = Pattern.compile("a+b");
          int m(List<String> lines) {
            int hits = 0;
            for (String line : lines) {
              if (WORD.matcher(line).find()) {
                hits++;
              }
              hits += line.split(",").length;
            }
            Pattern once = Pattern.compile("outside-loop");
            return hits + once.pattern().length();
          }
        }
        """);
  }

  @Test
  void keysetGetFlagged() {
    RuleTestHarness.assertFixture(new KeysetGetRule(), "P2", """
        import java.util.Map;
        class P2 {
          int m(Map<String, Integer> scores) {
            int total = 0;
            for (String key : scores.keySet()) {
              total += scores.get(key); // :: CK-KEYSET-GET
            }
            return total;
          }
        }
        """);
  }

  @Test
  void entrySetAndKeyOnlyLoopsAllowed() {
    RuleTestHarness.assertFixture(new KeysetGetRule(), "N2", """
        import java.util.Map;
        class N2 {
          int m(Map<String, Integer> scores) {
            int total = 0;
            for (Map.Entry<String, Integer> e : scores.entrySet()) {
              total += e.getValue();
            }
            for (String key : scores.keySet()) {
              total += key.length();
            }
            return total;
          }
        }
        """);
  }

  @Test
  void newWrapperFlagged() {
    RuleTestHarness.assertFixture(new NewWrapperRule(), "P3", """
        class P3 {
          Object[] m(String s) {
            Integer a = new Integer(42); // :: CK-NEW-WRAPPER
            Boolean b = new Boolean(true); // :: CK-NEW-WRAPPER
            String c = new String(s); // :: CK-NEW-WRAPPER
            return new Object[] {a, b, c};
          }
        }
        """);
  }

  @Test
  void valueOfAndCharArrayStringAllowed() {
    RuleTestHarness.assertFixture(new NewWrapperRule(), "N3", """
        class N3 {
          Object[] m(char[] chars) {
            Integer a = Integer.valueOf(42);
            Boolean b = Boolean.TRUE;
            String c = new String(chars);
            return new Object[] {a, b, c};
          }
        }
        """);
  }

  @Test
  void boxedAccumulatorFlagged() {
    RuleTestHarness.assertFixture(new BoxedAccumulatorRule(), "P4", """
        import java.util.List;
        class P4 {
          Long m(List<Integer> values) {
            Long total = 0L;
            for (int v : values) {
              total += v; // :: CK-BOXED-ACCUMULATOR
            }
            return total;
          }
        }
        """);
  }

  @Test
  void primitiveAccumulatorAllowed() {
    RuleTestHarness.assertFixture(new BoxedAccumulatorRule(), "N4", """
        import java.util.List;
        class N4 {
          long m(List<Integer> values) {
            long total = 0L;
            for (int v : values) {
              total += v;
            }
            Long outside = 0L;
            outside += 1;
            return total + outside;
          }
        }
        """);
  }
}
