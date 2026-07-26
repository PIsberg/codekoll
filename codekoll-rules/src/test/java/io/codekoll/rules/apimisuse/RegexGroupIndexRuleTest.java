package io.codekoll.rules.apimisuse;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class RegexGroupIndexRuleTest {

  private final RegexGroupIndexRule rule = new RegexGroupIndexRule();

  @Test
  void flagsGroupBeyondTheCapturingCount() {
    RuleTestHarness.assertFixture(rule, "P1", """
        import java.util.regex.Matcher;
        import java.util.regex.Pattern;
        class P1 {
          String m(String line) {
            Pattern p = Pattern.compile("(\\\\d{4})-(\\\\d{2})");
            Matcher matcher = p.matcher(line);
            return matcher.group(3); // :: CK-REGEX-GROUP-INDEX
          }
        }
        """);
  }

  @Test
  void flagsGroupCountedFromNonCapturingGroups() {
    RuleTestHarness.assertFixture(rule, "P2", """
        import java.util.regex.Matcher;
        import java.util.regex.Pattern;
        class P2 {
          String m(String line) {
            Pattern p = Pattern.compile("(?:GET|POST) (/\\\\w+)");
            Matcher matcher = p.matcher(line);
            return matcher.group(2); // :: CK-REGEX-GROUP-INDEX
          }
        }
        """);
  }

  @Test
  void flagsGroupOnAStaticFinalPattern() {
    RuleTestHarness.assertFixture(rule, "P3", """
        import java.util.regex.Matcher;
        class P3 {
          private static final java.util.regex.Pattern ID =
              java.util.regex.Pattern.compile("id-(\\\\d+)");
          String m(String line) {
            Matcher matcher = ID.matcher(line);
            return matcher.group(2); // :: CK-REGEX-GROUP-INDEX
          }
        }
        """);
  }

  @Test
  void flagsGroupOnAnInlineCompiledPattern() {
    RuleTestHarness.assertFixture(rule, "P4", """
        import java.util.regex.Matcher;
        import java.util.regex.Pattern;
        class P4 {
          String m(String line) {
            Matcher matcher = Pattern.compile("v(\\\\d+)").matcher(line);
            return matcher.group(2); // :: CK-REGEX-GROUP-INDEX
          }
        }
        """);
  }

  @Test
  void flagsReplacementReferenceBeyondTheGroupCount() {
    RuleTestHarness.assertFixture(rule, "P5", """
        class P5 {
          String m(String name) {
            return name.replaceAll("(\\\\w+) (\\\\w+)", "$3, $1"); // :: CK-REGEX-GROUP-INDEX
          }
        }
        """);
  }

  @Test
  void flagsReplacementReferenceAgainstALookahead() {
    RuleTestHarness.assertFixture(rule, "P6", """
        class P6 {
          String m(String text) {
            return text.replaceFirst("(?=x)(y)", "$2"); // :: CK-REGEX-GROUP-INDEX
          }
        }
        """);
  }

  @Test
  void allowsGroupsWithinRange() {
    RuleTestHarness.assertFixture(rule, "N1", """
        import java.util.regex.Matcher;
        import java.util.regex.Pattern;
        class N1 {
          String m(String line) {
            Pattern p = Pattern.compile("(\\\\d{4})-(\\\\d{2})");
            Matcher matcher = p.matcher(line);
            return matcher.group(0) + matcher.group(1) + matcher.group(2);
          }
        }
        """);
  }

  @Test
  void allowsNamedGroupsWhichAreAlsoNumbered() {
    RuleTestHarness.assertFixture(rule, "N2", """
        import java.util.regex.Matcher;
        import java.util.regex.Pattern;
        class N2 {
          String m(String line) {
            Pattern p = Pattern.compile("(?<year>\\\\d{4})-(?<month>\\\\d{2})");
            Matcher matcher = p.matcher(line);
            return matcher.group(2) + matcher.group("year");
          }
        }
        """);
  }

  @Test
  void allowsParenthesesInsideACharacterClass() {
    RuleTestHarness.assertFixture(rule, "N3", """
        import java.util.regex.Matcher;
        import java.util.regex.Pattern;
        class N3 {
          String m(String line) {
            Pattern p = Pattern.compile("[()](\\\\w+)");
            Matcher matcher = p.matcher(line);
            return matcher.group(1);
          }
        }
        """);
  }

  @Test
  void allowsEscapedParentheses() {
    RuleTestHarness.assertFixture(rule, "N4", """
        import java.util.regex.Matcher;
        import java.util.regex.Pattern;
        class N4 {
          String m(String line) {
            Pattern p = Pattern.compile("\\\\((\\\\w+)\\\\)");
            Matcher matcher = p.matcher(line);
            return matcher.group(1);
          }
        }
        """);
  }

  @Test
  void allowsNonConstantPatterns() {
    RuleTestHarness.assertFixture(rule, "N5", """
        import java.util.regex.Matcher;
        import java.util.regex.Pattern;
        class N5 {
          String m(String source, String line) {
            Pattern p = Pattern.compile(source);
            Matcher matcher = p.matcher(line);
            return matcher.group(7);
          }
        }
        """);
  }

  @Test
  void allowsAnEscapedDollarInAReplacement() {
    RuleTestHarness.assertFixture(rule, "N6", """
        class N6 {
          String m(String text) {
            return text.replaceAll("(\\\\w+)", "\\\\$1 is $1");
          }
        }
        """);
  }

  @Test
  void allowsGroupOnAnUntrackedMatcher() {
    RuleTestHarness.assertFixture(rule, "N7", """
        import java.util.regex.Matcher;
        class N7 {
          String m(Matcher matcher) {
            return matcher.group(5);
          }
        }
        """);
  }
}
