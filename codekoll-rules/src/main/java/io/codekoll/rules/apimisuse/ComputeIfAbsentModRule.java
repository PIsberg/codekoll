package io.codekoll.rules.apimisuse;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * CK-COMPUTE-IF-ABSENT-MOD: the lambda passed to {@code Map.computeIfAbsent} (and friends)
 * modifies the same map — HashMap throws ConcurrentModificationException.
 */
public final class ComputeIfAbsentModRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-COMPUTE-IF-ABSENT-MOD");

  private static final Set<String> COMPUTE_METHODS =
      Set.of("computeIfAbsent", "computeIfPresent", "compute", "merge");
  private static final Set<String> MAP_MUTATORS =
      Set.of("put", "remove", "clear", "putAll", "putIfAbsent", "merge",
          "computeIfAbsent", "compute", "computeIfPresent");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.API_MISUSE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Map modified inside its own computeIfAbsent lambda";
  }

  @Override
  public String explanation() {
    return "The mapping function of computeIfAbsent runs mid-operation, while the map is in "
        + "an inconsistent state. Modifying the same map inside it throws "
        + "ConcurrentModificationException on HashMap (since JDK 9), and silently corrupted "
        + "the table before that — a nested-cache-population bug that looks perfectly "
        + "reasonable.";
  }

  @Override
  public String fix() {
    return "Compute the value without touching the map, return it, and do any further map "
        + "updates after computeIfAbsent returns.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 2
            && node.getMethodSelect() instanceof MemberSelectTree select
            && COMPUTE_METHODS.contains(select.getIdentifier().toString())
            && isMap(select.getExpression(), ctx)
            && node.getArguments().get(1) instanceof LambdaExpressionTree lambda) {
          String mapExpr = select.getExpression().toString();
          MethodInvocationTree mutation = findSelfMutation(lambda, mapExpr);
          if (mutation != null) {
            ctx.report(mutation, "Modifying '" + mapExpr + "' inside its own "
                + select.getIdentifier() + " lambda throws "
                + "ConcurrentModificationException. Compute the value, then update the map "
                + "after.");
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isMap(com.sun.source.tree.ExpressionTree receiver, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), receiver));
        return ctx.isSubtypeOf(type, "java.util.Map");
      }

      private @Nullable MethodInvocationTree findSelfMutation(LambdaExpressionTree lambda,
          String mapExpr) {
        return lambda.getBody().accept(new TreeScanner<MethodInvocationTree, Void>() {
          @Override
          public MethodInvocationTree visitMethodInvocation(MethodInvocationTree call,
              Void unused) {
            if (call.getMethodSelect() instanceof MemberSelectTree select
                && MAP_MUTATORS.contains(select.getIdentifier().toString())
                && select.getExpression().toString().equals(mapExpr)) {
              return call;
            }
            return super.visitMethodInvocation(call, unused);
          }

          @Override
          public MethodInvocationTree reduce(@Nullable MethodInvocationTree a,
              @Nullable MethodInvocationTree b) {
            return a != null ? a : b;
          }
        }, null);
      }
    };
  }
}
