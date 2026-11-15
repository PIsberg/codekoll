package io.codekoll.rules.deadcode;

import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.IfTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;

/** CK-DEAD-BRANCH: a branch that a literal condition makes unreachable. */
public final class DeadBranchRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-DEAD-BRANCH");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.DEAD_CODE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Branch that a literal condition makes unreachable";
  }

  @Override
  public String explanation() {
    return "The condition is built only from literals, so one branch can never run. "
        + "Named constants and method calls are not evaluated.";
  }

  @Override
  public String fix() {
    return "Delete the dead branch, or fix the condition if a variable was meant.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitIf(IfTree node, RuleContext ctx) {
        switch (LiteralConditions.valueOf(node.getCondition())) {
          case FALSE -> ctx.report(node.getThenStatement(),
              "Dead branch: the condition is always false, so the then-branch never runs");
          case TRUE -> {
            if (node.getElseStatement() != null) {
              ctx.report(node.getElseStatement(),
                  "Dead branch: the condition is always true, so the else-branch never runs");
            }
          }
          case UNKNOWN -> { }
        }
        return super.visitIf(node, ctx);
      }

      @Override
      public Void visitConditionalExpression(ConditionalExpressionTree node, RuleContext ctx) {
        switch (LiteralConditions.valueOf(node.getCondition())) {
          case FALSE -> ctx.report(node.getTrueExpression(),
              "Dead branch: the condition is always false, so the then-arm never runs");
          case TRUE -> ctx.report(node.getFalseExpression(),
              "Dead branch: the condition is always true, so the else-arm never runs");
          case UNKNOWN -> { }
        }
        return super.visitConditionalExpression(node, ctx);
      }
    };
  }
}
