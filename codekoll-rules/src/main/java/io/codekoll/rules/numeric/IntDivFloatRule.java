package io.codekoll.rules.numeric;

import com.sun.source.tree.BinaryTree;
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
 * CK-INT-DIV-FLOAT: {@code int / int} whose result initializes a {@code double}/{@code float}
 * variable — the truncation already happened before the widening: {@code double r = 2 / 5;}
 * is 0.0, not 0.4.
 */
public final class IntDivFloatRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-INT-DIV-FLOAT");

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
    return "Integer division assigned to a floating-point variable";
  }

  @Override
  public String explanation() {
    return "Both operands are ints, so the division truncates FIRST and only then widens: "
        + "double ratio = hits / total; yields 0.0 for any hits < total. Percentages and "
        + "rates computed this way are silently zero (or whole-valued), which dashboards "
        + "happily display as 0%.";
  }

  @Override
  public String fix() {
    return "Widen an operand before dividing: (double) hits / total, or hits * 1.0 / total.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitVariable(VariableTree node, RuleContext ctx) {
        if (node.getInitializer() != null
            && NullFacts.unwrap(node.getInitializer()) instanceof BinaryTree binary
            && binary.getKind() == Tree.Kind.DIVIDE
            && isFloatingTarget(node, ctx)
            && isIntegral(binary.getLeftOperand(), ctx)
            && isIntegral(binary.getRightOperand(), ctx)) {
          ctx.report(node, "Integer division truncates BEFORE widening to "
              + node.getType() + " — the fraction is already gone. "
              + "Cast an operand first: (double) a / b.");
        }
        return super.visitVariable(node, ctx);
      }

      private boolean isFloatingTarget(VariableTree node, RuleContext ctx) {
        TreePath typePath = ctx.trees().getPath(ctx.unit(), node.getType());
        TypeMirror type = typePath == null ? null : ctx.typeOf(typePath);
        return type != null
            && (type.getKind() == TypeKind.DOUBLE || type.getKind() == TypeKind.FLOAT);
      }

      private boolean isIntegral(com.sun.source.tree.ExpressionTree operand, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), operand));
        return type != null && (type.getKind() == TypeKind.INT
            || type.getKind() == TypeKind.LONG || type.getKind() == TypeKind.SHORT
            || type.getKind() == TypeKind.BYTE || type.getKind() == TypeKind.CHAR);
      }
    };
  }
}
