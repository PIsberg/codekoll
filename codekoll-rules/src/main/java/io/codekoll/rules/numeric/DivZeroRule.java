package io.codekoll.rules.numeric;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;

/**
 * CK-DIV-ZERO: division or modulo by the integer literal 0 — guaranteed
 * {@code ArithmeticException} on every execution (it compiled only because the expression
 * is not a constant expression).
 */
public final class DivZeroRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-DIV-ZERO");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.NUMERIC;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Integer division or modulo by literal zero";
  }

  @Override
  public String explanation() {
    return "The divisor is the integer literal 0, so the expression throws "
        + "ArithmeticException: / by zero on every single execution. It compiled only "
        + "because a non-constant operand kept the compiler from folding it.";
  }

  @Override
  public String fix() {
    return "Fix the divisor — it is never legitimately a hardcoded integer zero.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitBinary(BinaryTree node, RuleContext ctx) {
        if ((node.getKind() == Tree.Kind.DIVIDE || node.getKind() == Tree.Kind.REMAINDER)
            && isIntegerZero(node.getRightOperand())) {
          ctx.report(node, "Division by literal zero: guaranteed ArithmeticException.");
        }
        return super.visitBinary(node, ctx);
      }

      @Override
      public Void visitCompoundAssignment(CompoundAssignmentTree node, RuleContext ctx) {
        if ((node.getKind() == Tree.Kind.DIVIDE_ASSIGNMENT
            || node.getKind() == Tree.Kind.REMAINDER_ASSIGNMENT)
            && isIntegerZero(node.getExpression())) {
          ctx.report(node, "Division by literal zero: guaranteed ArithmeticException.");
        }
        return super.visitCompoundAssignment(node, ctx);
      }

      private boolean isIntegerZero(ExpressionTree expr) {
        return NullFacts.unwrap(expr) instanceof LiteralTree literal
            && ((literal.getValue() instanceof Integer i && i == 0)
                || (literal.getValue() instanceof Long l && l == 0L));
      }
    };
  }
}
