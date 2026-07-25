package io.codekoll.rules.numeric;

import com.sun.source.tree.BinaryTree;
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
 * CK-SHIFT-OOB: shifting an int by a constant ≥ 32 (or a long by ≥ 64) — the JLS takes the
 * shift distance mod 32/64, so {@code 1 << 32 == 1}, never 0.
 */
public final class ShiftOobRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-SHIFT-OOB");

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
    return "Shift distance >= width of the shifted type";
  }

  @Override
  public String explanation() {
    return "Java takes the shift distance modulo the type width: for an int, 1 << 32 is "
        + "1 << 0 == 1, and 1 << 33 is 2 — never zero, never an overflow, just a silently "
        + "wrong value. Bit-mask and flag code built on such shifts computes garbage.";
  }

  @Override
  public String fix() {
    return "Shift a long (1L << n) when you need more than 31 bits, or fix the constant.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitBinary(BinaryTree node, RuleContext ctx) {
        if (node.getKind() == Tree.Kind.LEFT_SHIFT
            || node.getKind() == Tree.Kind.RIGHT_SHIFT
            || node.getKind() == Tree.Kind.UNSIGNED_RIGHT_SHIFT) {
          Integer distance = intConstant(node.getRightOperand());
          if (distance != null) {
            TypeMirror leftType =
                ctx.typeOf(new TreePath(getCurrentPath(), node.getLeftOperand()));
            int width = leftType != null && leftType.getKind() == TypeKind.LONG ? 64 : 32;
            if (distance >= width) {
              ctx.report(node, "Shift distance " + distance + " is taken mod " + width
                  + " (JLS): this shifts by " + (distance % width)
                  + ". Use a long, or fix the constant.");
            }
          }
        }
        return super.visitBinary(node, ctx);
      }

      private Integer intConstant(com.sun.source.tree.ExpressionTree expr) {
        return NullFacts.unwrap(expr) instanceof LiteralTree literal
            && literal.getValue() instanceof Integer i ? i : null;
      }
    };
  }
}
