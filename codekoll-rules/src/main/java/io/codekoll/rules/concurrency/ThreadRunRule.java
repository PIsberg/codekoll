package io.codekoll.rules.concurrency;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.type.TypeMirror;

/**
 * CK-THREAD-RUN: calling {@code Thread.run()} executes the runnable on the current thread;
 * the author almost certainly meant {@code start()}. Exemption: {@code super.run()} inside a
 * {@code Thread} subclass's own {@code run()} override.
 */
public final class ThreadRunRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-THREAD-RUN");

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
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Thread.run() called directly instead of start()";
  }

  @Override
  public String explanation() {
    return "Thread.run() is a plain method call: the body executes synchronously on the "
        + "CURRENT thread. No new thread is started, so the code silently loses all "
        + "concurrency it was written for.";
  }

  @Override
  public String fix() {
    return "Call start() to launch the new thread; keep run() only for the thread's own body.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().isEmpty()
            && node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("run")) {
          ExpressionTree receiver = select.getExpression();
          if (!isSuperKeyword(receiver)) {
            TypeMirror receiverType =
                ctx.typeOf(new TreePath(getCurrentPath(), receiver));
            if (ctx.isSubtypeOf(receiverType, "java.lang.Thread")) {
              ctx.report(node, "Thread.run() executes on the CURRENT thread. "
                  + "Did you mean start()?");
            }
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }
    };
  }

  private static boolean isSuperKeyword(ExpressionTree receiver) {
    return receiver.getKind() == Tree.Kind.IDENTIFIER && "super".equals(receiver.toString());
  }
}
