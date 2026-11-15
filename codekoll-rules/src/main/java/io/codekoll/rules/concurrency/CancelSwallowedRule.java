package io.codekoll.rules.concurrency;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
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
 * CK-CANCEL-SWALLOWED: cancellation that disappears, either into a {@code catch (Exception)} /
 * {@code catch (Throwable)} around an interruptible call, or into a
 * {@code CompletionStage.exceptionally}/{@code handle} lambda that never reads its throwable.
 */
public final class CancelSwallowedRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-CANCEL-SWALLOWED");

  private static final Set<String> BROAD = Set.of("java.lang.Exception", "java.lang.Throwable");

  /** Recovery methods that turn a failure into a value, and their throwable parameter index. */
  private static final Map<String, Integer> RECOVERY = Map.of(
      "exceptionally", 0, "exceptionallyAsync", 0, "handle", 1, "handleAsync", 1);

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
    return "Cancellation or interrupt swallowed by a broad catch or a future fallback";
  }

  @Override
  public String explanation() {
    return "Executors, Future.cancel(true) and StructuredTaskScope cancel work by interrupting "
        + "it. A catch (Exception) around a blocking call catches that InterruptedException "
        + "along with everything else, clears the interrupt flag and carries on, so the "
        + "cancelled task keeps running and shutdown or scope.join() waits for it. A "
        + "CompletableFuture.exceptionally/handle fallback that ignores its throwable does the "
        + "same to a cancelled pipeline: CancellationException becomes an ordinary value.";
  }

  @Override
  public String fix() {
    return "Catch InterruptedException first and call Thread.currentThread().interrupt() (or "
        + "rethrow); in a future fallback, inspect the throwable and let CancellationException "
        + "propagate.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitTry(TryTree node, RuleContext ctx) {
        TreePath tryPath = getCurrentPath();
        boolean handled = false;
        for (CatchTree clause : node.getCatches()) {
          TreePath catchPath = new TreePath(tryPath, clause);
          if (InterruptSignals.catches(catchPath, InterruptSignals.INTERRUPTED, ctx)
              || InterruptSignals.catches(catchPath, InterruptSignals.CANCELLED, ctx)) {
            handled = true;
          } else if (!handled && isBroad(catchPath, ctx)
              && swallows(clause)
              && callsInterruptible(new TreePath(tryPath, node.getBlock()), ctx)
              && !InterruptSignals.atThreadEntryEnd(catchPath, ctx)) {
            ctx.report(clause, "This catch (" + clause.getParameter().getType() + ") also "
                + "catches the InterruptedException a cancelled task receives, clears the "
                + "interrupt and carries on. Catch InterruptedException first and call "
                + "Thread.currentThread().interrupt(), or rethrow.");
          }
        }
        return super.visitTry(node, ctx);
      }

      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && node.getArguments().size() == 1
            && node.getArguments().getFirst() instanceof LambdaExpressionTree lambda) {
          int index = RECOVERY.getOrDefault(select.getIdentifier().toString(), -1);
          TreePath receiver = new TreePath(getCurrentPath(), select.getExpression());
          if (index >= 0 && lambda.getParameters().size() > index
              && ctx.isSubtypeOf(ctx.typeOf(receiver), "java.util.concurrent.CompletionStage")
              && !reads(lambda, lambda.getParameters().get(index))) {
            ctx.report(node, select.getIdentifier() + " ignores its throwable, so a "
                + "CancellationException becomes an ordinary value and the cancelled pipeline "
                + "looks successful. Inspect the throwable and let cancellation propagate.");
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }
    };
  }

  private static boolean isBroad(TreePath catchPath, RuleContext ctx) {
    return InterruptSignals.caughtTypes(catchPath).stream()
        .anyMatch(type -> BROAD.contains(ctx.qualifiedNameOf(ctx.typeOf(type))));
  }

  private static boolean swallows(CatchTree clause) {
    return !InterruptSignals.containsThrow(clause.getBlock())
        && !InterruptSignals.callsInterrupt(clause.getBlock())
        && !InterruptSignals.testsForCancellation(clause.getBlock());
  }

  /** A call in the try block, outside nested lambdas and classes, declaring InterruptedException. */
  private static boolean callsInterruptible(TreePath block, RuleContext ctx) {
    Boolean found = new TreePathScanner<Boolean, Void>() {
      @Override
      public Boolean visitMethodInvocation(MethodInvocationTree node, Void unused) {
        return declaresInterrupted(getCurrentPath()) || super.visitMethodInvocation(node, unused);
      }

      @Override
      public Boolean visitNewClass(NewClassTree node, Void unused) {
        return declaresInterrupted(getCurrentPath()) || super.visitNewClass(node, unused);
      }

      @Override
      public @Nullable Boolean visitLambdaExpression(LambdaExpressionTree node, Void unused) {
        return null;
      }

      @Override
      public @Nullable Boolean visitClass(ClassTree node, Void unused) {
        return null;
      }

      private boolean declaresInterrupted(TreePath call) {
        @Nullable Element element = ctx.trees().getElement(call);
        return element instanceof ExecutableElement method && method.getThrownTypes().stream()
            .anyMatch(thrown -> ctx.isSubtypeOf(thrown, InterruptSignals.INTERRUPTED));
      }

      @Override
      public Boolean reduce(@Nullable Boolean a, @Nullable Boolean b) {
        return Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b);
      }
    }.scan(block, null);
    return Boolean.TRUE.equals(found);
  }

  private static boolean reads(LambdaExpressionTree lambda, VariableTree parameter) {
    String name = parameter.getName().toString();
    Boolean found = lambda.getBody().accept(new TreeScanner<Boolean, Void>() {
      @Override
      public Boolean visitIdentifier(IdentifierTree node, Void unused) {
        return node.getName().contentEquals(name);
      }

      @Override
      public Boolean reduce(@Nullable Boolean a, @Nullable Boolean b) {
        return Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b);
      }
    }, null);
    return Boolean.TRUE.equals(found);
  }
}
