package io.codekoll.rules.nullness;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

/**
 * CK-NULL-TO-NONNULL: a null literal passed to a parameter annotated {@code @NonNull}, or
 * returned from a method annotated {@code @NonNull}. Fires only when the annotation is
 * actually present (no inference).
 */
public final class NullToNonnullRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-NULL-TO-NONNULL");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.NULLNESS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "null passed to (or returned as) a @NonNull";
  }

  @Override
  public String explanation() {
    return "The target is explicitly annotated @NonNull, documenting that it never accepts "
        + "or produces null. Passing/returning null violates that contract: the callee "
        + "(or the caller of a @NonNull return) skips null checks by design and NPEs "
        + "somewhere downstream, far from here.";
  }

  @Override
  public String fix() {
    return "Provide a non-null value; if null is genuinely valid, change the annotation to "
        + "@Nullable and handle absence at every use site.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        Element called = ctx.trees().getElement(getCurrentPath());
        if (called instanceof ExecutableElement method) {
          List<? extends VariableElement> params = method.getParameters();
          List<? extends com.sun.source.tree.ExpressionTree> args = node.getArguments();
          for (int i = 0; i < args.size() && i < params.size(); i++) {
            if (args.get(i).getKind() == Tree.Kind.NULL_LITERAL
                && hasNonNull(params.get(i).asType())) {
              ctx.report(args.get(i), "null passed to @NonNull parameter '"
                  + params.get(i).getSimpleName() + "'. Provide a non-null value.");
            }
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }

      @Override
      public Void visitReturn(ReturnTree node, RuleContext ctx) {
        if (node.getExpression() != null
            && node.getExpression().getKind() == Tree.Kind.NULL_LITERAL) {
          ExecutableElement method = enclosingMethod(ctx);
          if (method != null && hasNonNull(method.getReturnType())) {
            ctx.report(node, "return null from @NonNull method '" + method.getSimpleName()
                + "'. Return a non-null value or mark the return @Nullable.");
          }
        }
        return super.visitReturn(node, ctx);
      }

      // JSpecify @NonNull is TYPE_USE, so it lives on the TypeMirror, not the element.
      private boolean hasNonNull(javax.lang.model.type.TypeMirror type) {
        return type.getAnnotationMirrors().stream()
            .map(a -> a.getAnnotationType().asElement().getSimpleName().toString())
            .anyMatch(n -> "NonNull".equals(n) || "Nonnull".equals(n) || "NotNull".equals(n));
      }

      private ExecutableElement enclosingMethod(RuleContext ctx) {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          if (p.getLeaf() instanceof com.sun.source.tree.LambdaExpressionTree) {
            return null;
          }
          if (p.getLeaf() instanceof com.sun.source.tree.MethodTree) {
            return ctx.trees().getElement(p) instanceof ExecutableElement m ? m : null;
          }
        }
        return null;
      }
    };
  }
}
