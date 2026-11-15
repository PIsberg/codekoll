package io.codekoll.rules.frameworks;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import org.jspecify.annotations.Nullable;

/**
 * CK-ASYNC-THREAD-CONTEXT: thread-bound context (security, MDC, locale, request) read on an
 * async thread, where the holder's {@code ThreadLocal} is empty.
 */
public final class AsyncThreadContextRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-ASYNC-THREAD-CONTEXT");

  /** Holder simple name to the methods that read its thread-bound state. */
  private static final Map<String, Set<String>> READS = Map.of(
      "SecurityContextHolder", Set.of("getContext"),
      "MDC", Set.of("get", "getCopyOfContextMap"),
      "LocaleContextHolder", Set.of("getLocale", "getTimeZone", "getLocaleContext"),
      "RequestContextHolder", Set.of("currentRequestAttributes", "getRequestAttributes"));

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.FRAMEWORKS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "Thread-bound context read on an async thread";
  }

  @Override
  public String explanation() {
    return "SecurityContextHolder, MDC, LocaleContextHolder and RequestContextHolder keep their "
        + "state in ThreadLocals filled for the request thread. Code in an @Async method or a "
        + "supplyAsync/thenApplyAsync lambda runs on another thread, where they are empty: the "
        + "authentication is null, MDC keys are missing from the logs, the locale is the "
        + "default, and currentRequestAttributes() throws IllegalStateException. Nothing fails "
        + "in a unit test that calls the method directly.";
  }

  @Override
  public String fix() {
    return "Read the value on the calling thread and pass it in (capture it in the lambda, or "
        + "as an @Async parameter), or configure a TaskDecorator that propagates the context.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public @Nullable Void visitMethod(MethodTree node, RuleContext ctx) {
        if (node.getBody() != null
            && Annotations.find(node.getModifiers(), "Async").isPresent()) {
          reportReads(new TreePath(getCurrentPath(), node.getBody()), ctx);
        }
        return super.visitMethod(node, ctx);
      }

      @Override
      public @Nullable Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (isAsyncStage(getCurrentPath(), ctx)) {
          for (ExpressionTree arg : node.getArguments()) {
            if (arg instanceof LambdaExpressionTree lambda) {
              reportReads(new TreePath(new TreePath(getCurrentPath(), lambda), lambda.getBody()),
                  ctx);
            }
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }
    };
  }

  private static boolean isAsyncStage(TreePath call, RuleContext ctx) {
    @Nullable Element element = ctx.trees().getElement(call);
    return element instanceof ExecutableElement method
        && method.getSimpleName().toString().endsWith("Async")
        && ctx.isSubtypeOf(method.getEnclosingElement().asType(),
            "java.util.concurrent.CompletionStage");
  }

  /** Context reads under {@code root}, not inside nested lambdas or classes. */
  private static void reportReads(TreePath root, RuleContext ctx) {
    new TreePathScanner<Void, Void>() {
      @Override
      public @Nullable Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        if (node.getMethodSelect() instanceof MemberSelectTree select) {
          String holder = Annotations.simpleName(select.getExpression());
          Set<String> reads = READS.get(holder);
          if (reads != null && reads.contains(select.getIdentifier().toString())) {
            ctx.report(node, holder + "." + select.getIdentifier() + " runs on an async thread, "
                + "where the request thread's context is not set: it reads empty or null. Read "
                + "it before the hand-off and pass the value in.");
          }
        }
        return super.visitMethodInvocation(node, unused);
      }

      @Override
      public @Nullable Void visitLambdaExpression(LambdaExpressionTree node, Void unused) {
        return null;
      }

      @Override
      public @Nullable Void visitClass(ClassTree node, Void unused) {
        return null;
      }
    }.scan(root, null);
  }
}
