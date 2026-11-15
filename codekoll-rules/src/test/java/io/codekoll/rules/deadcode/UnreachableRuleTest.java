package io.codekoll.rules.deadcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class UnreachableRuleTest {

  private final UnreachableRule rule = new UnreachableRule();

  @Test
  void metadata() { // 010 FR-005, FR-007, FR-010 (never ERROR severity)
    assertEquals("CK-UNREACHABLE", rule.id().value());
    assertEquals(RulePack.DEAD_CODE, rule.pack());
    assertEquals(Severity.WARNING, rule.defaultSeverity());
    assertFalse(rule.description().isBlank() || rule.description().equals("TODO"));
    assertFalse(rule.explanation().isBlank() || rule.explanation().equals("TODO"));
    assertFalse(rule.fix().isBlank() || rule.fix().equals("TODO"));
  }

  @Test
  void flagsStatementAfterIfTrueReturn() { // 010 FR-001 US1-scenario-1
    RuleTestHarness.assertFixture(rule, "P1", """
        class P1 {
          void m() {
            if (true) { return; }
            System.out.println("x"); // :: CK-UNREACHABLE
          }
        }
        """);
  }

  @Test
  void messageNamesTheStatement() { // 010 FR-001 FR-005
    String source = """
        class P2 {
          void m() {
            if (true) { return; }
            System.out.println("x");
          }
        }
        """;
    var findings = RuleTestHarness.run(rule, "P2", source).findings();
    assertEquals(1, findings.size());
    assertEquals(4, findings.get(0).line());
    String msg = findings.get(0).message().toLowerCase();
    assertTrue(msg.contains("unreachable") && msg.contains("println"), msg);
  }

  @Test
  void flagsAfterIfTrueThrow() { // 010 FR-001
    RuleTestHarness.assertFixture(rule, "P3", """
        class P3 {
          void m() {
            if (true) { throw new IllegalStateException(); }
            System.out.println("x"); // :: CK-UNREACHABLE
          }
        }
        """);
  }

  @Test
  void flagsAfterIfTrueBreakAndContinue() { // 010 FR-001
    RuleTestHarness.assertFixture(rule, "P4", """
        class P4 {
          void m() {
            while (System.nanoTime() > 0) {
              if (true) { break; }
              System.out.println("a"); // :: CK-UNREACHABLE
            }
            for (int i = 0; i < 3; i++) {
              if (true) { continue; }
              System.out.println("b"); // :: CK-UNREACHABLE
            }
          }
        }
        """);
  }

  @Test
  void flagsAfterIfTrueYield() { // 010 FR-001
    RuleTestHarness.assertFixture(rule, "P5", """
        class P5 {
          int m(int x) {
            return switch (x) {
              case 1 -> {
                if (true) { yield 1; }
                yield 2; // :: CK-UNREACHABLE
              }
              default -> 0;
            };
          }
        }
        """);
  }

  @Test
  void flagsUnbracedThenBranch() { // 010 FR-001
    RuleTestHarness.assertFixture(rule, "P6", """
        class P6 {
          void m() {
            if (true) return;
            System.out.println("x"); // :: CK-UNREACHABLE
          }
        }
        """);
  }

  @Test
  void flagsWhenExitIsLastStatementOfNestedBlock() { // 010 FR-001
    RuleTestHarness.assertFixture(rule, "P7", """
        class P7 {
          void m() {
            if (true) {
              System.out.println("a");
              { return; }
            }
            System.out.println("b"); // :: CK-UNREACHABLE
          }
        }
        """);
  }

  @Test
  void flagsInsideSwitchCase() { // 010 FR-001
    RuleTestHarness.assertFixture(rule, "P8", """
        class P8 {
          void m(int x) {
            switch (x) {
              case 1:
                if (true) { break; }
                System.out.println("a"); // :: CK-UNREACHABLE
                break;
              default:
                break;
            }
          }
        }
        """);
  }

  @Test
  void reportsOnlyTheNextStatement() { // 010 FR-001
    RuleTestHarness.assertFixture(rule, "P9", """
        class P9 {
          void m() {
            if (true) { return; }
            System.out.println("a"); // :: CK-UNREACHABLE
            System.out.println("b");
          }
        }
        """);
  }

  @Test
  void silentWhenCorrected() { // 010 FR-001 US1-scenario-3
    RuleTestHarness.assertFixture(rule, "N1", """
        class N1 {
          void m(boolean c) {
            if (c) { return; }
            System.out.println("x");
          }
        }
        """);
  }

  @Test
  void silentWhenThenBranchCompletesNormally() { // 010 FR-001 US1-scenario-3
    RuleTestHarness.assertFixture(rule, "N2", """
        class N2 {
          void m() {
            if (true) { System.out.println("a"); }
            System.out.println("b");
          }
        }
        """);
  }

  @Test
  void silentWhenIfHasElse() { // 010 FR-001
    RuleTestHarness.assertFixture(rule, "N3", """
        class N3 {
          void m() {
            if (true) { return; } else { System.out.println("a"); }
            System.out.println("b");
          }
        }
        """);
  }

  @Test
  void silentForNamedConstantCondition() { // 010 FR-001 US1-scenario-3 (D2)
    RuleTestHarness.assertFixture(rule, "N4", """
        class N4 {
          static final boolean DEBUG = true;
          void m() {
            if (DEBUG) { return; }
            System.out.println("x");
          }
        }
        """);
  }

  @Test
  void silentWhenIfIsLastStatement() { // 010 FR-001
    RuleTestHarness.assertFixture(rule, "N5", """
        class N5 {
          void m() {
            System.out.println("a");
            if (true) { return; }
          }
        }
        """);
  }

  @Test
  void silentForEmptyMethod() { // 010 FR-001
    RuleTestHarness.assertFixture(rule, "N6", """
        class N6 {
          void m() {}
        }
        """);
  }

  @Test
  void emptyThenBlockIsNotReportedAndDoesNotCrash() { // 010 FR-001
    RuleTestHarness.assertFixture(rule, "N7", """
        class N7 {
          void m() {
            if (true) {}
            System.out.println("x");
          }
        }
        """);
  }

  @Test
  void arrowCaseBodiesDoNotCrash() { // 010 FR-001
    RuleTestHarness.assertFixture(rule, "N8", """
        class N8 {
          void m(int x) {
            switch (x) {
              case 1 -> { if (true) { return; } }
              case 2 -> System.out.println("b");
              default -> throw new IllegalStateException();
            }
          }
        }
        """);
  }
}
