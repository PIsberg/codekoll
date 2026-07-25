package io.codekoll.rules.correctness;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.NewClassTree;
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
 * CK-BIGDECIMAL-DOUBLE: {@code new BigDecimal(double)} inherits binary floating-point
 * imprecision — {@code new BigDecimal(0.1)} is 0.1000000000000000055511151231257827.
 */
public final class BigdecimalDoubleRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-BIGDECIMAL-DOUBLE");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "new BigDecimal(double) carries binary imprecision into exact arithmetic";
  }

  @Override
  public String explanation() {
    return "The double 0.1 is not exactly 0.1 — new BigDecimal(0.1) faithfully preserves "
        + "the binary approximation: 0.1000000000000000055511151231257827... The whole "
        + "point of BigDecimal (exact money math) is defeated at construction; totals "
        + "drift by fractions of a cent that auditors do notice.";
  }

  @Override
  public String fix() {
    return "Use BigDecimal.valueOf(0.1) (goes through the double's canonical string) or "
        + "new BigDecimal(\"0.1\") for full control.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitNewClass(NewClassTree node, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), node));
        if ("java.math.BigDecimal".equals(ctx.qualifiedNameOf(type))
            && node.getArguments().size() == 1
            && isFloatingPoint(node.getArguments().get(0), ctx)) {
          ctx.report(node, "new BigDecimal(double) preserves the binary approximation "
              + "(0.1 -> 0.1000...0555...). Use BigDecimal.valueOf or the String "
              + "constructor.");
        }
        return super.visitNewClass(node, ctx);
      }

      private boolean isFloatingPoint(ExpressionTree arg, RuleContext ctx) {
        ExpressionTree unwrapped = NullFacts.unwrap(arg);
        if (unwrapped.getKind() == Tree.Kind.DOUBLE_LITERAL
            || unwrapped.getKind() == Tree.Kind.FLOAT_LITERAL) {
          return true;
        }
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), arg));
        return type != null
            && (type.getKind() == TypeKind.DOUBLE || type.getKind() == TypeKind.FLOAT);
      }
    };
  }
}
