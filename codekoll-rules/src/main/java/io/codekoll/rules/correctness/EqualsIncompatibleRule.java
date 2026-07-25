package io.codekoll.rules.correctness;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import io.codekoll.rules.support.Types2;
import javax.lang.model.type.TypeMirror;

/**
 * CK-EQUALS-INCOMPATIBLE: {@code a.equals(b)} where a and b have provably unrelated types —
 * always false at runtime.
 */
public final class EqualsIncompatibleRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-EQUALS-INCOMPATIBLE");

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
    return "equals() between provably unrelated types (always false)";
  }

  @Override
  public String explanation() {
    return "When the receiver and argument types have no subtype relationship, equals() "
        + "can only ever return false — a well-behaved equals rejects a different class "
        + "outright. The comparison silently never matches: a Long compared to an Integer, "
        + "a String to a StringBuilder, an enum to its name.";
  }

  @Override
  public String fix() {
    return "Compare compatible types — convert one side (Long.valueOf(i), sb.toString()) "
        + "or use the value you actually meant to compare.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 1
            && node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("equals")) {
          TypeMirror receiver = typeOf(select.getExpression(), ctx);
          TypeMirror arg = typeOf(node.getArguments().get(0), ctx);
          if (Types2.provablyUnrelated(receiver, arg, ctx)) {
            ctx.report(node, "equals() between " + ctx.qualifiedNameOf(receiver)
                .replaceFirst(".*\\.", "") + " and " + ctx.qualifiedNameOf(arg)
                .replaceFirst(".*\\.", "") + " is always false — the types are unrelated.");
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private TypeMirror typeOf(ExpressionTree expr, RuleContext ctx) {
        return ctx.typeOf(new TreePath(getCurrentPath(), expr));
      }
    };
  }
}
