package io.codekoll.rules.concurrency;

import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.type.TypeMirror;

/**
 * CK-WAIT-NO-LOOP: {@code Object.wait()}/{@code Condition.await()} not inside a loop —
 * spurious wakeups make an un-looped wait incorrect.
 */
public final class WaitNoLoopRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-WAIT-NO-LOOP");

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
    return "wait()/await() not guarded by a loop";
  }

  @Override
  public String explanation() {
    return "wait() and Condition.await() can return SPURIOUSLY — without any notify, and "
        + "with the condition still false. Called in an if (or bare), the thread proceeds "
        + "as though the condition holds when it may not, corrupting the very state the "
        + "wait was protecting. The condition must be re-checked in a loop.";
  }

  @Override
  public String fix() {
    return "Wait in a loop: while (!condition) { lock.wait(); } — re-check on every wakeup.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().isEmpty()
            && node.getMethodSelect() instanceof MemberSelectTree select
            && (select.getIdentifier().contentEquals("wait")
                || select.getIdentifier().contentEquals("await"))
            && isWaitReceiver(select, ctx)
            && !insideLoop()) {
          ctx.report(node, select.getIdentifier() + "() can wake spuriously — re-check the "
              + "condition in a while loop: while (!ready) { ... " + select.getIdentifier()
              + "(); }.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isWaitReceiver(MemberSelectTree select, RuleContext ctx) {
        TypeMirror receiver =
            ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
        if (select.getIdentifier().contentEquals("await")) {
          return ctx.isSubtypeOf(receiver, "java.util.concurrent.locks.Condition");
        }
        return receiver != null;  // Object.wait — any receiver
      }

      private boolean insideLoop() {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          Tree leaf = p.getLeaf();
          if (leaf instanceof com.sun.source.tree.MethodTree) {
            return false;
          }
          if (leaf instanceof WhileLoopTree || leaf instanceof DoWhileLoopTree
              || leaf instanceof ForLoopTree || leaf instanceof EnhancedForLoopTree) {
            return true;
          }
        }
        return false;
      }
    };
  }
}
