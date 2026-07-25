package io.codekoll.rules.concurrency;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.element.Modifier;

/**
 * CK-SLEEP-IN-SYNC: {@code Thread.sleep} inside a {@code synchronized} block or method —
 * the monitor is held for the whole sleep, stalling every thread that needs it.
 */
public final class SleepInSyncRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-SLEEP-IN-SYNC");

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
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "Thread.sleep while holding a monitor";
  }

  @Override
  public String explanation() {
    return "sleep() does not release the monitor (unlike wait()). Every other thread "
        + "needing this lock blocks for the full sleep duration — a single sleeping thread "
        + "serializes the whole system on that lock, which shows up as mysterious latency "
        + "spikes under load.";
  }

  @Override
  public String fix() {
    return "Sleep outside the synchronized section, or use lock.newCondition().await(timeout) "
        + "which releases the lock while waiting.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("sleep")
            && select.getExpression().toString().endsWith("Thread")
            && isInsideSynchronized()) {
          ctx.report(node, "Thread.sleep holds the monitor for the whole sleep — every "
              + "thread needing this lock stalls. Sleep outside the lock or use "
              + "Condition.await(timeout).");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isInsideSynchronized() {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          Tree leaf = p.getLeaf();
          if (leaf instanceof SynchronizedTree) {
            return true;
          }
          if (leaf instanceof MethodTree method) {
            return method.getModifiers().getFlags().contains(Modifier.SYNCHRONIZED);
          }
        }
        return false;
      }
    };
  }
}
