package io.codekoll.rules.correctness;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * CK-OPTIONAL-NULL: {@code return null;} from a method declared to return {@code Optional}
 * defeats the type's entire purpose — callers guard with {@code isPresent()}, never against
 * null, and NPE on the "safe" API.
 */
public final class OptionalNullRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-OPTIONAL-NULL");

  private static final Set<String> OPTIONAL_TYPES = Set.of(
      "java.util.Optional", "java.util.OptionalInt", "java.util.OptionalLong",
      "java.util.OptionalDouble");

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
    return "return null from an Optional-returning method";
  }

  @Override
  public String explanation() {
    return "Optional exists so that absence is a value, never null. Callers of an "
        + "Optional-returning method write opt.isPresent()/opt.map(...) and do not "
        + "null-check — returning null makes exactly those safe-looking call sites throw "
        + "NullPointerException.";
  }

  @Override
  public String fix() {
    return "Return Optional.empty() (or OptionalInt.empty() etc.) for the absent case.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitReturn(ReturnTree node, RuleContext ctx) {
        if (node.getExpression() != null
            && node.getExpression().getKind() == Tree.Kind.NULL_LITERAL) {
          MethodTree method = enclosingMethodStoppingAtLambda(getCurrentPath());
          if (method != null && method.getReturnType() != null) {
            TreePath methodPath = ctx.trees().getPath(ctx.unit(), method.getReturnType());
            String returnType = methodPath == null ? ""
                : ctx.qualifiedNameOf(ctx.typeOf(methodPath));
            if (OPTIONAL_TYPES.contains(returnType)) {
              ctx.report(node, "return null defeats Optional — callers never null-check an "
                  + "Optional. Return Optional.empty() instead.");
            }
          }
        }
        return super.visitReturn(node, ctx);
      }

      private @Nullable MethodTree enclosingMethodStoppingAtLambda(TreePath from) {
        for (TreePath p = from; p != null; p = p.getParentPath()) {
          if (p.getLeaf() instanceof LambdaExpressionTree) {
            return null;  // the lambda's own return type governs, not the method's
          }
          if (p.getLeaf() instanceof MethodTree method) {
            return method;
          }
        }
        return null;
      }
    };
  }
}
