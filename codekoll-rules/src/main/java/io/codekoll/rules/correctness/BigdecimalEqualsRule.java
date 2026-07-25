package io.codekoll.rules.correctness;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * CK-BIGDECIMAL-EQUALS: {@code equals}/{@code hashCode} on BigDecimal — scale-sensitive, so
 * {@code 1.0} is not equal to {@code 1.00}; most callers mean {@code compareTo(...) == 0}.
 */
public final class BigdecimalEqualsRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-BIGDECIMAL-EQUALS");

  private static final Set<String> METHODS = Set.of("equals");

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
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "BigDecimal.equals is scale-sensitive (1.0 != 1.00)";
  }

  @Override
  public String explanation() {
    return "BigDecimal.equals compares scale as well as value: "
        + "new BigDecimal(\"1.0\").equals(new BigDecimal(\"1.00\")) is FALSE. Two amounts "
        + "that are numerically equal but parsed with different precision fail the check — "
        + "a reconciliation that should balance reports a difference of zero cents.";
  }

  @Override
  public String fix() {
    return "Use a.compareTo(b) == 0 for value equality; keep equals only when scale itself "
        + "matters (and then say so).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 1
            && node.getMethodSelect() instanceof MemberSelectTree select
            && METHODS.contains(select.getIdentifier().toString())
            && isBigDecimal(select.getExpression(), ctx)
            && isBigDecimal(node.getArguments().get(0), ctx)) {
          ctx.report(node, "BigDecimal.equals is scale-sensitive (1.0 != 1.00). "
              + "Use compareTo(...) == 0 for value equality.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isBigDecimal(com.sun.source.tree.ExpressionTree expr, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), expr));
        return "java.math.BigDecimal".equals(ctx.qualifiedNameOf(type));
      }
    };
  }
}
