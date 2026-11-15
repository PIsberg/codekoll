package io.codekoll.rules.deadcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class DeadBranchRuleTest {

  private final DeadBranchRule rule = new DeadBranchRule();

  @Test
  void metadata() { // 010 FR-005, FR-007
    assertEquals("CK-DEAD-BRANCH", rule.id().value());
    assertEquals(RulePack.DEAD_CODE, rule.pack());
    assertEquals(Severity.WARNING, rule.defaultSeverity());
    assertFalse(rule.description().isBlank() || rule.description().equals("TODO"));
    assertFalse(rule.explanation().isBlank() || rule.explanation().equals("TODO"));
    assertFalse(rule.fix().isBlank() || rule.fix().equals("TODO"));
  }

  @Test
  void flagsIfFalse() { // 010 FR-002 US1-scenario-2
    RuleTestHarness.assertFixture(rule, "P1", """
        class P1 {
          void m() {
            if (false) { // :: CK-DEAD-BRANCH
              System.out.println("x");
            }
          }
        }
        """);
  }

  @Test
  void flagsLiteralComparisonFalse() { // 010 FR-002 US1-scenario-2
    RuleTestHarness.assertFixture(rule, "P2", """
        class P2 {
          void m() {
            if (1 > 2) { // :: CK-DEAD-BRANCH
              System.out.println("x");
            }
          }
        }
        """);
  }

  @Test
  void flagsNegatedAndCompoundLiterals() { // 010 FR-002 US1-scenario-2
    RuleTestHarness.assertFixture(rule, "P3", """
        class P3 {
          void m() {
            if (!true) { // :: CK-DEAD-BRANCH
              System.out.println("a");
            }
            if (true && false) { // :: CK-DEAD-BRANCH
              System.out.println("b");
            }
            if (2L == 3L || 'a' > 'b') { // :: CK-DEAD-BRANCH
              System.out.println("c");
            }
          }
        }
        """);
  }

  @Test
  void flagsElseOfIfTrue() { // 010 FR-002 US1-scenario-2
    RuleTestHarness.assertFixture(rule, "P4", """
        class P4 {
          int m() {
            if (true) {
              return 1;
            } else { // :: CK-DEAD-BRANCH
              return 2;
            }
          }
        }
        """);
  }

  @Test
  void flagsDeadTernaryArm() { // 010 FR-002 US1-scenario-2
    RuleTestHarness.assertFixture(rule, "P5", """
        class P5 {
          int m() {
            return false ? 1 : 2; // :: CK-DEAD-BRANCH
          }
        }
        """);
  }

  @Test
  void messageNamesDeadBranch() { // 010 FR-005
    var result = RuleTestHarness.run(rule, "P6", """
        class P6 {
          void m() {
            if (false) {
              System.out.println("x");
            }
          }
        }
        """);
    assertEquals(1, result.findings().size());
    assertTrue(result.findings().get(0).message().toLowerCase().contains("then"));
    var elseResult = RuleTestHarness.run(rule, "P7", """
        class P7 {
          int m() {
            if (true) { return 1; } else { return 2; }
          }
        }
        """);
    assertEquals(1, elseResult.findings().size());
    assertTrue(elseResult.findings().get(0).message().toLowerCase().contains("else"));
  }

  @Test
  void silentWhenReachable() { // 010 FR-002 US1-scenario-3
    RuleTestHarness.assertFixture(rule, "N1", """
        class N1 {
          void m(int x) {
            if (x > 2) {
              System.out.println("a");
            }
            if (true) {
              System.out.println("b");
            }
            int y = x > 1 ? 1 : 2;
            System.out.println(y);
          }
        }
        """);
  }

  @Test
  void silentOnNamedConstantFlags() { // 010 FR-002 FR-008 US1-scenario-4
    RuleTestHarness.assertFixture(rule, "N2", """
        class N2 {
          static final boolean DEBUG = false;
          void m() {
            if (DEBUG) {
              System.out.println("a");
            }
            if (!DEBUG) {
              System.out.println("b");
            }
            if (DEBUG && true) {
              System.out.println("c");
            }
            int v = DEBUG ? 1 : 2;
            System.out.println(v);
          }
        }
        """);
  }

  @Test
  void silentOnDoWhileFalse() { // 010 FR-002 FR-008
    RuleTestHarness.assertFixture(rule, "N3", """
        class N3 {
          void m() {
            do {
              System.out.println("once");
            } while (false);
          }
        }
        """);
  }

  @Test
  void silentOnCallAndStringOperands() { // 010 FR-008
    RuleTestHarness.assertFixture(rule, "N4", """
        class N4 {
          boolean f() { return false; }
          void m() {
            if (f()) {
              System.out.println("a");
            }
            if ("a" == "b") {
              System.out.println("b");
            }
            if (Boolean.FALSE) {
              System.out.println("c");
            }
          }
        }
        """);
  }

  @Test
  void silentOnDefaultOnlySwitchAndEmptyMethod() { // 010 FR-008
    RuleTestHarness.assertFixture(rule, "N5", """
        class N5 {
          void a(int x) {
            switch (x) {
              default:
                System.out.println("d");
            }
          }
          void b() {
          }
        }
        """);
  }
}
