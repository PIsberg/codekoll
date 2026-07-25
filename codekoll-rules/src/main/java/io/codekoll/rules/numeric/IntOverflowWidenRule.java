package io.codekoll.rules.numeric;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * CK-INT-OVERFLOW-WIDEN: an int*int multiplication whose result flows into a long context —
 * the multiply already overflowed in 32 bits before widening.
 */
public final class IntOverflowWidenRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-INT-OVERFLOW-WIDEN");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "int multiplication widened to long AFTER it overflows";
  }

  @Override
  public String explanation() {
    return "int * int is computed in 32 bits, then widened. long ms = days * 86_400_000 "
        + "overflows at ~24 days before the result ever becomes a long — the widening "
        + "preserves the wrong (wrapped, often negative) value. Millisecond and byte-size "
        + "computations are the usual victims.";
  }

  @Override
  public String fix() {
    return "Make one operand long so the arithmetic is 64-bit from the start: "
        + "days * 86_400_000L, or (long) days * 86_400_000.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitVariable(VariableTree node, RuleContext ctx) {
        if (node.getInitializer() != null && isLongType(node.getType())) {
          checkExpression(node.getInitializer(), ctx);
        }
        return super.visitVariable(node, ctx);
      }

      @Override
      public Void visitReturn(ReturnTree node, RuleContext ctx) {
        if (node.getExpression() != null && enclosingMethodReturnsLong()) {
          checkExpression(node.getExpression(), ctx);
        }
        return super.visitReturn(node, ctx);
      }

      @Override
      public Void visitAssignment(AssignmentTree node, RuleContext ctx) {
        if (isLongTyped(node.getVariable(), ctx)) {
          checkExpression(node.getExpression(), ctx);
        }
        return super.visitAssignment(node, ctx);
      }

      private void checkExpression(ExpressionTree expr, RuleContext ctx) {
        ExpressionTree e = NullFacts.unwrap(expr);
        if (e instanceof BinaryTree binary && binary.getKind() == Tree.Kind.MULTIPLY
            && isIntTyped(binary, ctx) && overflowPlausible(binary)) {
          ctx.report(binary, "int*int overflows in 32 bits BEFORE widening to long. "
              + "Make one operand long: x * 86_400_000L.");
        }
      }

      /** At least one operand is a large literal or a non-constant int (overflow plausible). */
      private boolean overflowPlausible(BinaryTree binary) {
        return operandRisky(binary.getLeftOperand()) || operandRisky(binary.getRightOperand());
      }

      private boolean operandRisky(ExpressionTree operand) {
        ExpressionTree e = NullFacts.unwrap(operand);
        if (e instanceof LiteralTree literal && literal.getValue() instanceof Integer i) {
          return Math.abs((long) i) >= 1000;
        }
        return !(e instanceof LiteralTree);  // non-constant int
      }

      private boolean isIntTyped(ExpressionTree expr, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), expr));
        return type != null && type.getKind() == TypeKind.INT;
      }

      private boolean isLongTyped(ExpressionTree expr, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), expr));
        return type != null && type.getKind() == TypeKind.LONG;
      }

      private boolean isLongType(Tree typeTree) {
        return typeTree instanceof com.sun.source.tree.PrimitiveTypeTree primitive
            && primitive.getPrimitiveTypeKind() == TypeKind.LONG;
      }

      private boolean enclosingMethodReturnsLong() {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          if (p.getLeaf() instanceof com.sun.source.tree.LambdaExpressionTree) {
            return false;
          }
          if (p.getLeaf() instanceof com.sun.source.tree.MethodTree method) {
            return isLongType(method.getReturnType());
          }
        }
        return false;
      }
    };
  }
}
