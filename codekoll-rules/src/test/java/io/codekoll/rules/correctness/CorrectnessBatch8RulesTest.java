package io.codekoll.rules.correctness;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class CorrectnessBatch8RulesTest {

  @Test
  void defaultCharsetFlagged() {
    RuleTestHarness.assertFixture(new DefaultCharsetRule(), "P1", """
        import java.io.FileReader;
        import java.io.IOException;
        class P1 {
          Object[] m(String s, byte[] raw, String path) throws IOException {
            byte[] bytes = s.getBytes(); // :: CK-DEFAULT-CHARSET
            String text = new String(raw); // :: CK-DEFAULT-CHARSET
            FileReader reader = new FileReader(path); // :: CK-DEFAULT-CHARSET
            reader.close();
            return new Object[] {bytes, text};
          }
        }
        """);
  }

  @Test
  void explicitCharsetAllowed() {
    RuleTestHarness.assertFixture(new DefaultCharsetRule(), "N1", """
        import java.nio.charset.StandardCharsets;
        class N1 {
          Object[] m(String s, byte[] raw) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            String text = new String(raw, StandardCharsets.UTF_8);
            return new Object[] {bytes, text};
          }
        }
        """);
  }

  @Test
  void equalsWithoutHashcodeFlagged() {
    RuleTestHarness.assertFixture(new EqualsHashcodeRule(), "P2", """
        class P2 { // :: CK-EQUALS-HASHCODE
          private final String id = "";
          @Override
          public boolean equals(Object o) {
            return o instanceof P2 other && id.equals(other.id);
          }
        }
        """);
  }

  @Test
  void bothOverriddenOrNeitherAllowed() {
    RuleTestHarness.assertFixture(new EqualsHashcodeRule(), "N2", """
        class N2 {
          private final String id = "";
          @Override
          public boolean equals(Object o) {
            return o instanceof N2 other && id.equals(other.id);
          }
          @Override
          public int hashCode() {
            return id.hashCode();
          }
          static class Plain {
            int x;
          }
        }
        """);
  }

  @Test
  void weekYearFlagged() {
    RuleTestHarness.assertFixture(new WeekYearFormatRule(), "P3", """
        import java.time.format.DateTimeFormatter;
        class P3 {
          DateTimeFormatter m() {
            return DateTimeFormatter.ofPattern("YYYY-MM-dd"); // :: CK-WEEK-YEAR-FORMAT
          }
        }
        """);
  }

  @Test
  void calendarYearAndWeekPatternsAllowed() {
    RuleTestHarness.assertFixture(new WeekYearFormatRule(), "N3", """
        import java.time.format.DateTimeFormatter;
        class N3 {
          DateTimeFormatter[] m() {
            return new DateTimeFormatter[] {
              DateTimeFormatter.ofPattern("yyyy-MM-dd"),
              DateTimeFormatter.ofPattern("YYYY-ww"),
            };
          }
        }
        """);
  }

  @Test
  void bigDecimalDoubleFlagged() {
    RuleTestHarness.assertFixture(new BigdecimalDoubleRule(), "P4", """
        import java.math.BigDecimal;
        class P4 {
          BigDecimal m(double amount) {
            BigDecimal a = new BigDecimal(0.1); // :: CK-BIGDECIMAL-DOUBLE
            BigDecimal b = new BigDecimal(amount); // :: CK-BIGDECIMAL-DOUBLE
            return a.add(b);
          }
        }
        """);
  }

  @Test
  void valueOfAndStringCtorAllowed() {
    RuleTestHarness.assertFixture(new BigdecimalDoubleRule(), "N4", """
        import java.math.BigDecimal;
        class N4 {
          BigDecimal m(double amount) {
            BigDecimal a = BigDecimal.valueOf(0.1);
            BigDecimal b = new BigDecimal("0.1");
            BigDecimal c = new BigDecimal(42);
            return a.add(b).add(c).add(BigDecimal.valueOf(amount));
          }
        }
        """);
  }

  @Test
  void assignInCondFlagged() {
    RuleTestHarness.assertFixture(new AssignInCondRule(), "P5", """
        class P5 {
          void m(boolean done) {
            if (done = true) { // :: CK-ASSIGN-IN-COND
              System.gc();
            }
          }
        }
        """);
  }

  @Test
  void comparisonsAndReadLoopIdiomAllowed() {
    RuleTestHarness.assertFixture(new AssignInCondRule(), "N5", """
        import java.io.BufferedReader;
        import java.io.IOException;
        class N5 {
          void m(boolean done, BufferedReader reader) throws IOException {
            if (done == true) {
              System.gc();
            }
            String line;
            while ((line = reader.readLine()) != null) {
              System.out.println(line);
            }
          }
        }
        """);
  }
}
