package io.codekoll.rules.correctness;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class SelfAssignRuleTest {

  private final SelfAssignRule rule = new SelfAssignRule();

  @Test
  void flagsLocalSelfAssign() {
    RuleTestHarness.assertFixture(rule, "P1", """
        class P1 {
          void m(int x) {
            x = x; // :: CK-SELF-ASSIGN
          }
        }
        """);
  }

  @Test
  void flagsFieldSelfAssignThroughThis() {
    RuleTestHarness.assertFixture(rule, "P2", """
        class P2 {
          private int count;
          void m() {
            this.count = count; // :: CK-SELF-ASSIGN
          }
        }
        """);
  }

  @Test
  void allowsParamToFieldAssignment() {
    RuleTestHarness.assertFixture(rule, "N1", """
        class N1 {
          private String name;
          N1(String name) {
            this.name = name;
          }
          String name() {
            return name;
          }
        }
        """);
  }

  @Test
  void allowsDifferentVariables() {
    RuleTestHarness.assertFixture(rule, "N2", """
        class N2 {
          void m(int a, int b) {
            a = b;
            System.out.println(a);
          }
        }
        """);
  }
}
