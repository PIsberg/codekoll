package io.codekoll.rules.concurrency;

import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.TryTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * CK-LOCK-NO-FINALLY: {@code lock.lock()} not immediately followed by a try whose finally
 * unlocks the same lock — an exception in the guarded region leaves the lock held forever.
 */
public final class LockNoFinallyRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-LOCK-NO-FINALLY");

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
    return "Lock.lock() without try/finally unlock";
  }

  @Override
  public String explanation() {
    return "If the guarded code throws before unlock(), the lock is never released — every "
        + "thread that later needs it blocks forever. Unlike synchronized, an explicit Lock "
        + "has no automatic release: one uncaught exception deadlocks the whole subsystem.";
  }

  @Override
  public String fix() {
    return "lock.lock(); try { ... } finally { lock.unlock(); } — the unlock must be in "
        + "finally so it runs on every exit path.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitExpressionStatement(ExpressionStatementTree node, RuleContext ctx) {
        if (node.getExpression() instanceof MethodInvocationTree call
            && call.getMethodSelect() instanceof MemberSelectTree select
            && (select.getIdentifier().contentEquals("lock")
                || select.getIdentifier().contentEquals("lockInterruptibly"))
            && isLock(select, ctx)) {
          String lockVar = select.getExpression().toString();
          if (!nextStatementIsUnlockingTry(node, lockVar)) {
            ctx.report(node, lockVar + ".lock() is not guarded by try/finally — an "
                + "exception leaves the lock held forever. Wrap the region: try { ... } "
                + "finally { " + lockVar + ".unlock(); }.");
          }
        }
        return super.visitExpressionStatement(node, ctx);
      }

      private boolean isLock(MemberSelectTree select, RuleContext ctx) {
        TypeMirror receiver =
            ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
        return ctx.isSubtypeOf(receiver, "java.util.concurrent.locks.Lock");
      }

      /** The statement right after lock() must be a try whose finally unlocks the lock. */
      private boolean nextStatementIsUnlockingTry(ExpressionStatementTree lockStatement,
          String lockVar) {
        StatementTree next = statementAfter(lockStatement);
        return next instanceof TryTree tryTree
            && tryTree.getFinallyBlock() != null
            && finallyUnlocks(tryTree, lockVar);
      }

      private @Nullable StatementTree statementAfter(ExpressionStatementTree statement) {
        TreePath parent = getCurrentPath().getParentPath();
        if (parent != null
            && parent.getLeaf() instanceof com.sun.source.tree.BlockTree block) {
          var statements = block.getStatements();
          int index = statements.indexOf(statement);
          if (index >= 0 && index + 1 < statements.size()) {
            return statements.get(index + 1);
          }
        }
        return null;
      }

      private boolean finallyUnlocks(TryTree tryTree, String lockVar) {
        return tryTree.getFinallyBlock().getStatements().stream()
            .anyMatch(s -> s instanceof ExpressionStatementTree expr
                && expr.getExpression() instanceof MethodInvocationTree call
                && call.getMethodSelect() instanceof MemberSelectTree select
                && select.getIdentifier().contentEquals("unlock")
                && select.getExpression().toString().equals(lockVar));
      }
    };
  }
}
