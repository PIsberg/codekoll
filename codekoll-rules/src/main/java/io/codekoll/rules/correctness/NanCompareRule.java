package io.codekoll.rules.correctness;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;

/**
 * CK-NAN-COMPARE: {@code x == Double.NaN} / {@code x != Float.NaN} — by IEEE 754, NaN
 * compares unequal to everything including itself, so the check is a constant.
 */
public final class NanCompareRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-NAN-COMPARE");
  private static final Set<String> NAN_OWNERS = Set.of("Double", "Float");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.CORRECTNESS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "== or != against Double.NaN is constant by IEEE 754";
  }

  @Override
  public String explanation() {
    return "IEEE 754 defines NaN as unequal to everything, itself included: x == Double.NaN "
        + "is ALWAYS false and x != Double.NaN is ALWAYS true, regardless of x. The NaN "
        + "guard the author wrote never detects anything.";
  }

  @Override
  public String fix() {
    return "Use Double.isNaN(x) / Float.isNaN(x).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitBinary(BinaryTree node, RuleContext ctx) {
        if ((node.getKind() == Tree.Kind.EQUAL_TO || node.getKind() == Tree.Kind.NOT_EQUAL_TO)
            && (isNanConstant(node.getLeftOperand()) || isNanConstant(node.getRightOperand()))) {
          ctx.report(node, "NaN compares unequal to everything (IEEE 754) — this is "
              + (node.getKind() == Tree.Kind.EQUAL_TO ? "always false" : "always true")
              + ". Use Double.isNaN(...).");
        }
        return super.visitBinary(node, ctx);
      }

      private boolean isNanConstant(ExpressionTree expr) {
        return NullFacts.unwrap(expr) instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("NaN")
            && NAN_OWNERS.contains(simpleName(select.getExpression()));
      }

      private String simpleName(ExpressionTree owner) {
        String text = owner.toString();
        int dot = text.lastIndexOf('.');
        return dot < 0 ? text : text.substring(dot + 1);
      }
    };
  }
}
