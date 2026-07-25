package io.codekoll.rules.concurrency;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
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
 * CK-CTOR-THREAD-START: {@code Thread.start()} inside a constructor publishes {@code this}
 * before construction completes.
 */
public final class CtorThreadStartRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-CTOR-THREAD-START");

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
    return "Thread started from inside a constructor";
  }

  @Override
  public String explanation() {
    return "A thread started in the constructor can begin running — and observing this — "
        + "before the constructor finishes: final fields may not be initialized (the JMM's "
        + "final-field guarantees only apply after construction completes). The new thread "
        + "sees half-built state on rare, timing-dependent runs.";
  }

  @Override
  public String fix() {
    return "Create the thread in the constructor but start it from a separate start()/init "
        + "method — or use a static factory that constructs fully, then starts.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().isEmpty()
            && node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("start")
            && insideConstructor()) {
          TypeMirror receiver =
              ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
          if (ctx.isSubtypeOf(receiver, "java.lang.Thread")) {
            ctx.report(node, "Starting a thread in the constructor publishes 'this' before "
                + "construction completes — the thread may see half-built state. Start it "
                + "from a separate method.");
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean insideConstructor() {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          Tree leaf = p.getLeaf();
          if (leaf instanceof com.sun.source.tree.LambdaExpressionTree) {
            return false;
          }
          if (leaf instanceof MethodTree method) {
            return method.getName().contentEquals("<init>");
          }
          if (leaf instanceof ClassTree) {
            return false;
          }
        }
        return false;
      }
    };
  }
}
