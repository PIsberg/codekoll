package io.codekoll.rules.concurrency;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import org.jspecify.annotations.Nullable;

/**
 * CK-INTERRUPT-NOT-RESTORED: a {@code catch (InterruptedException)} that neither restores the
 * interrupt status nor rethrows. Exempt at the end of a thread entry point, where nobody is left
 * to read the flag.
 */
public final class InterruptNotRestoredRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-INTERRUPT-NOT-RESTORED");

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
    return "InterruptedException caught without restoring the interrupt status";
  }

  @Override
  public String explanation() {
    return "Throwing InterruptedException clears the thread's interrupt flag. A catch block that "
        + "logs it, wraps it in an unchecked exception or ignores it leaves the thread looking "
        + "as if nobody asked it to stop: executors' shutdownNow(), Future.cancel(true) and "
        + "structured-concurrency scopes all cancel by interrupting, so the task keeps running, "
        + "the next blocking call waits normally, and shutdown hangs until it times out.";
  }

  @Override
  public String fix() {
    return "Call Thread.currentThread().interrupt() in the catch block before logging or "
        + "wrapping, or let InterruptedException propagate (declare it, or rethrow it).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitCatch(CatchTree node, RuleContext ctx) {
        TreePath path = getCurrentPath();
        if (InterruptSignals.catches(path, InterruptSignals.INTERRUPTED, ctx)
            && !InterruptSignals.callsInterrupt(node.getBlock())
            && !InterruptSignals.rethrows(node.getBlock(), node.getParameter().getName().toString())
            && !throwsNewInterrupted(path, ctx)
            && !InterruptSignals.atThreadEntryEnd(path, ctx)) {
          ctx.report(node, "Catching InterruptedException cleared this thread's interrupt status, "
              + "and this block neither restores nor rethrows it, so callers never learn the "
              + "thread was asked to stop. Call Thread.currentThread().interrupt() first, or "
              + "rethrow.");
        }
        return super.visitCatch(node, ctx);
      }

      private boolean throwsNewInterrupted(TreePath catchPath, RuleContext ctx) {
        CatchTree node = (CatchTree) catchPath.getLeaf();
        TreePath block = new TreePath(catchPath, node.getBlock());
        Boolean found = new TreePathScanner<Boolean, Void>() {
          @Override
          public Boolean visitThrow(ThrowTree throwTree, Void unused) {
            if (throwTree.getExpression() instanceof NewClassTree created) {
              TreePath createdPath = new TreePath(getCurrentPath(), created);
              if (ctx.isSubtypeOf(ctx.typeOf(createdPath), InterruptSignals.INTERRUPTED)) {
                return Boolean.TRUE;
              }
            }
            return super.visitThrow(throwTree, unused);
          }

          @Override
          public Boolean reduce(@Nullable Boolean a, @Nullable Boolean b) {
            return Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b);
          }
        }.scan(block, null);
        return Boolean.TRUE.equals(found);
      }
    };
  }
}
