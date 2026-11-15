package io.codekoll.rules.deadcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class UnusedLocalRuleTest {

  private final UnusedLocalRule rule = new UnusedLocalRule();

  @Test
  void metadata() { // 010 FR-005, FR-007
    assertEquals("CK-UNUSED-LOCAL", rule.id().value());
    assertEquals(RulePack.DEAD_CODE, rule.pack());
    assertEquals(Severity.INFO, rule.defaultSeverity());
    assertFalse(rule.description().isBlank() || rule.description().equals("TODO"));
    assertFalse(rule.explanation().isBlank() || rule.explanation().equals("TODO"));
    assertFalse(rule.fix().isBlank() || rule.fix().equals("TODO"));
  }

  @Test
  void flagsNeverUsedLocal() { // 010 FR-003
    RuleTestHarness.assertFixture(rule, "P1", """
        class P1 {
          void m() {
            int count = 5; // :: CK-UNUSED-LOCAL
          }
        }
        """);
  }

  @Test
  void flagsWriteOnlyLocalWithNeverReadWording() { // 010 FR-003 FR-005
    String source = """
        class P2 {
          void m() {
            int total = 0; // :: CK-UNUSED-LOCAL
            total = 5;
          }
        }
        """;
    RuleTestHarness.assertFixture(rule, "P2", source);
    var findings = RuleTestHarness.run(rule, "P2", source).findings();
    assertEquals(1, findings.size());
    String msg = findings.get(0).message();
    assertTrue(msg.contains("total") && msg.toLowerCase().contains("never read"), msg);
  }

  @Test
  void flagsOnlyTheWriteOnlyLocal() { // 010 FR-003
    RuleTestHarness.assertFixture(rule, "P3", """
        class P3 {
          int m() {
            int a = 1;
            int dead = 2; // :: CK-UNUSED-LOCAL
            dead = a;
            return a;
          }
        }
        """);
  }

  @Test
  void compoundAssignmentAndIncrementCountAsReads() { // 010 FR-003
    RuleTestHarness.assertFixture(rule, "N1", """
        class N1 {
          void m() {
            int a = 0;
            a += 1;
            int b = 0;
            b++;
          }
        }
        """);
  }

  @Test
  void readInsideLambdaOrLocalClassCounts() { // 010 FR-003
    RuleTestHarness.assertFixture(rule, "N2", """
        class N2 {
          Runnable m() {
            int a = 1;
            int b = 2;
            class L { int g() { return b; } }
            System.out.println(new L().g());
            return () -> System.out.println(a);
          }
        }
        """);
  }

  @Test
  void arrayElementWriteKeepsArrayAndIndexUsed() { // 010 FR-003
    RuleTestHarness.assertFixture(rule, "N3", """
        class N3 {
          void m(int v) {
            int[] arr = new int[2];
            int i = 0;
            arr[i] = v;
          }
        }
        """);
  }

  @Test
  void exemptEnhancedForVariable() { // 010 FR-008
    RuleTestHarness.assertFixture(rule, "N4", """
        class N4 {
          void m(java.util.List<String> xs) {
            for (String s : xs) {
              System.out.println("x");
            }
          }
        }
        """);
  }

  @Test
  void exemptUnnamedVariable() { // 010 FR-008
    RuleTestHarness.assertFixture(rule, "N5", """
        class N5 {
          int f() { return 1; }
          void m() {
            int _ = f();
          }
        }
        """, 25);
  }

  @Test
  void exemptAnnotatedLocal() { // 010 FR-008
    RuleTestHarness.assertFixture(rule, "N6", """
        class N6 {
          void m() {
            @SuppressWarnings("unused") int a = 1;
          }
        }
        """);
  }

  @Test
  void exemptIgnoredAndUnusedNames() { // 010 FR-008
    RuleTestHarness.assertFixture(rule, "N7", """
        class N7 {
          void m() {
            int ignored = 1;
            int ignoredResult = 2;
            int unused = 3;
            int unusedValue = 4;
          }
        }
        """);
  }

  @Test
  void otherVariableKindsAreNeverConsidered() { // 010 FR-008
    RuleTestHarness.assertFixture(rule, "N8", """
        class N8 {
          void m(int param, Object o) throws Exception {
            try (java.io.StringReader r = new java.io.StringReader("x")) {
              System.out.println("a");
            } catch (RuntimeException e) {
              System.out.println("b");
            }
            if (o instanceof String s) {
              System.out.println("c");
            }
          }
        }
        """);
  }
}
