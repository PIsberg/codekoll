package io.codekoll.rules.modern;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * CK-STRUCTURED-GET-BEFORE-JOIN: {@code Subtask.get()} called, in statement order, before
 * the enclosing scope's {@code join()} — throws IllegalStateException every time.
 */
public final class StructuredGetBeforeJoinRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-STRUCTURED-GET-BEFORE-JOIN");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.MODERN;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Subtask.get() called before StructuredTaskScope.join()";
  }

  @Override
  public String explanation() {
    return "A StructuredTaskScope subtask has no result until the scope's join() has "
        + "returned. Calling subtask.get() first throws IllegalStateException: 'Owner did "
        + "not join after forking' — every time. The fork/join/get ordering is the whole "
        + "contract of structured concurrency.";
  }

  @Override
  public String fix() {
    return "Order it fork -> join() -> get(): collect subtasks, call scope.join() (and "
        + "throwIfFailed), then read each subtask's get().";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethod(MethodTree node, RuleContext ctx) {
        if (node.getBody() != null) {
          analyze(node, ctx);
        }
        return super.visitMethod(node, ctx);
      }

      private void analyze(MethodTree method, RuleContext ctx) {
        java.util.List<MethodInvocationTree> calls = new java.util.ArrayList<>();
        method.getBody().accept(new TreeScanner<Void, Void>() {
          @Override
          public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
            calls.add(call);
            return super.visitMethodInvocation(call, unused);
          }
        }, null);
        boolean joined = false;
        for (MethodInvocationTree call : calls) {
          if (isJoin(call)) {
            joined = true;
          } else if (!joined && isSubtaskGet(call, ctx)) {
            ctx.report(call, "Subtask.get() before the scope's join() throws "
                + "IllegalStateException. Call scope.join() first.");
          }
        }
      }

      private boolean isJoin(MethodInvocationTree call) {
        return call.getMethodSelect() instanceof MemberSelectTree select
            && (select.getIdentifier().contentEquals("join")
                || select.getIdentifier().contentEquals("joinUntil"));
      }

      private boolean isSubtaskGet(MethodInvocationTree call, RuleContext ctx) {
        if (!(call.getMethodSelect() instanceof MemberSelectTree select)
            || !select.getIdentifier().contentEquals("get")
            || !call.getArguments().isEmpty()) {
          return false;
        }
        TypeMirror receiver = receiverType(select, ctx);
        return receiver != null
            && ctx.qualifiedNameOf(receiver).contains("StructuredTaskScope.Subtask");
      }

      private @Nullable TypeMirror receiverType(MemberSelectTree select, RuleContext ctx) {
        return ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
      }
    };
  }
}
