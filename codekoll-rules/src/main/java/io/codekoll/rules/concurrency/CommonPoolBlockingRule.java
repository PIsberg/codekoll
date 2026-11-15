package io.codekoll.rules.concurrency;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.BlockingCalls;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import org.jspecify.annotations.Nullable;

/**
 * CK-COMMONPOOL-BLOCKING: a blocking call inside a task that runs on
 * {@code ForkJoinPool.commonPool()}: a default-executor async stage or a parallel stream.
 */
public final class CommonPoolBlockingRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-COMMONPOOL-BLOCKING");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.CONCURRENCY;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Blocking call on the shared ForkJoinPool common pool";
  }

  @Override
  public String explanation() {
    return "CompletableFuture.supplyAsync/runAsync and every *Async stage without an Executor, "
        + "and every parallel stream, run on ForkJoinPool.commonPool(): one pool, sized to the "
        + "core count, shared by the whole JVM. A task that sleeps, waits on a queue, or does "
        + "JDBC or socket I/O holds one of those few threads for the whole wait. A handful of "
        + "them stall every other parallel stream and async task in the application, often in "
        + "unrelated code, and the latency shows up far from the cause.";
  }

  @Override
  public String fix() {
    return "Pass a dedicated Executor to the async call "
        + "(Executors.newVirtualThreadPerTaskExecutor() suits I/O), or use a sequential stream "
        + "for I/O-bound work.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public @Nullable Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        TreePath path = getCurrentPath();
        @Nullable String where = commonPoolUse(node, path, ctx);
        if (where != null) {
          for (ExpressionTree arg : node.getArguments()) {
            if (arg instanceof LambdaExpressionTree lambda) {
              TreePath body = new TreePath(new TreePath(path, lambda), lambda.getBody());
              for (TreePath call : BlockingCalls.declaring(body, ctx, BlockingCalls.BLOCKING)) {
                ctx.report(call.getLeaf(), "This blocking call runs on the shared common pool ("
                    + where + "), holding one of its few threads for the whole wait and "
                    + "stalling every other async task and parallel stream. Pass a dedicated "
                    + "Executor, or use a sequential stream.");
              }
            }
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }
    };
  }

  /** How {@code node} reaches the common pool, or null when it does not. */
  private static @Nullable String commonPoolUse(MethodInvocationTree node, TreePath path,
      RuleContext ctx) {
    @Nullable Element element = ctx.trees().getElement(path);
    if (element instanceof ExecutableElement method
        && method.getSimpleName().toString().endsWith("Async")
        && ctx.isSubtypeOf(method.getEnclosingElement().asType(),
            "java.util.concurrent.CompletionStage")
        && method.getParameters().stream()
            .noneMatch(p -> ctx.isSubtypeOf(p.asType(), "java.util.concurrent.Executor"))) {
      return method.getSimpleName() + " without an Executor";
    }
    if (node.getMethodSelect() instanceof MemberSelectTree select
        && ctx.isSubtypeOf(ctx.typeOf(new TreePath(path, select.getExpression())),
            "java.util.stream.BaseStream")
        && parallelInChain(select.getExpression())) {
      return "a parallel stream";
    }
    return null;
  }

  /** Walks the receiver chain; the nearest parallel()/sequential() switch decides. */
  private static boolean parallelInChain(ExpressionTree receiver) {
    ExpressionTree expr = receiver;
    while (expr instanceof MethodInvocationTree call
        && call.getMethodSelect() instanceof MemberSelectTree select) {
      String name = select.getIdentifier().toString();
      if ("sequential".equals(name)) {
        return false;
      }
      if ("parallel".equals(name) || "parallelStream".equals(name)) {
        return true;
      }
      expr = select.getExpression();
    }
    return false;
  }
}
