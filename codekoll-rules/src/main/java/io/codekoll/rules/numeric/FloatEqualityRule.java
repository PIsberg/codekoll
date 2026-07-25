package io.codekoll.rules.numeric;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
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
 * CK-FLOAT-EQUALITY: {@code ==}/{@code !=} between two floating-point expressions — usually
 * wants an epsilon comparison. Comparisons with the literal 0.0 are exempt.
 */
public final class FloatEqualityRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-FLOAT-EQUALITY");

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
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "Exact == between floating-point values";
  }

  @Override
  public String explanation() {
    return "Floating-point arithmetic rounds: 0.1 + 0.2 != 0.3. Two computations that are "
        + "mathematically equal routinely differ in the last bits, so exact == is false "
        + "when the logic expects true — loop exits and threshold checks misfire on "
        + "specific, hard-to-reproduce values.";
  }

  @Override
  public String fix() {
    return "Compare with a tolerance: Math.abs(a - b) < 1e-9 — or use BigDecimal/longs "
        + "(cents, not euros) where exactness matters.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitBinary(BinaryTree node, RuleContext ctx) {
        if ((node.getKind() == Tree.Kind.EQUAL_TO || node.getKind() == Tree.Kind.NOT_EQUAL_TO)
            && isFloating(node.getLeftOperand(), ctx)
            && isFloating(node.getRightOperand(), ctx)
            && !isZeroLiteral(node.getLeftOperand())
            && !isZeroLiteral(node.getRightOperand())
            && !isNanConstant(node.getLeftOperand())
            && !isNanConstant(node.getRightOperand())) {
          ctx.report(node, "Exact floating-point == misfires on rounding (0.1 + 0.2 != 0.3). "
              + "Compare with a tolerance: Math.abs(a - b) < eps.");
        }
        return super.visitBinary(node, ctx);
      }

      private boolean isFloating(ExpressionTree operand, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), operand));
        return type != null
            && (type.getKind() == TypeKind.DOUBLE || type.getKind() == TypeKind.FLOAT);
      }

      private boolean isZeroLiteral(ExpressionTree operand) {
        return NullFacts.unwrap(operand) instanceof LiteralTree literal
            && literal.getValue() instanceof Number n
            && n.doubleValue() == 0.0;
      }

      /** NaN comparisons are CK-NAN-COMPARE's more specific diagnosis — don't double-report. */
      private boolean isNanConstant(ExpressionTree operand) {
        return NullFacts.unwrap(operand)
            instanceof com.sun.source.tree.MemberSelectTree select
            && select.getIdentifier().contentEquals("NaN");
      }
    };
  }
}
