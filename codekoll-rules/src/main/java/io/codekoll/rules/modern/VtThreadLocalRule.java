package io.codekoll.rules.modern;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * CK-VT-THREADLOCAL: a {@code ThreadLocal} read or written inside a task handed to a virtual
 * thread, where every task gets a fresh, empty copy that dies with it.
 */
public final class VtThreadLocalRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-VT-THREADLOCAL");

  private static final Set<String> ACCESS = Set.of("get", "set", "remove");
  private static final Set<String> SUBMIT = Set.of("submit", "execute");

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
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "ThreadLocal used inside a virtual-thread task";
  }

  @Override
  public String explanation() {
    return "A virtual thread is created for one task and discarded after it, starting with empty "
        + "thread-locals. A ThreadLocal used as a per-thread cache (withInitial(SimpleDateFormat"
        + "::new), a buffer, a connection) is rebuilt for every task and never reused, so the "
        + "cache costs an allocation per task instead of saving one; with millions of virtual "
        + "threads the copies also add up in memory. Per-task context passed through a "
        + "ThreadLocal is what ScopedValue is designed for.";
  }

  @Override
  public String fix() {
    return "Pass per-task context with ScopedValue.where(KEY, value).run(task); share a "
        + "thread-safe object (DateTimeFormatter) or a bounded pool instead of a per-thread cache.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public @Nullable Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (startsVirtualThread(node)) {
          for (ExpressionTree arg : node.getArguments()) {
            if (arg instanceof LambdaExpressionTree lambda) {
              TreePath body = new TreePath(new TreePath(getCurrentPath(), lambda),
                  lambda.getBody());
              reportAccesses(body, ctx);
            }
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean startsVirtualThread(MethodInvocationTree node) {
        if (!(node.getMethodSelect() instanceof MemberSelectTree select)) {
          return false;
        }
        String name = select.getIdentifier().toString();
        ExpressionTree receiver = select.getExpression();
        if ("startVirtualThread".equals(name)) {
          return true;
        }
        if ("start".equals(name) || "unstarted".equals(name)) {
          return receiver.toString().contains("ofVirtual");
        }
        if (SUBMIT.contains(name)) {
          return createsVirtualExecutor(receiver)
              || receiver instanceof IdentifierTree id && localIsVirtualExecutor(id);
        }
        return false;
      }

      private boolean localIsVirtualExecutor(IdentifierTree id) {
        @Nullable MethodTree method = enclosingMethod();
        if (method == null || method.getBody() == null) {
          return false;
        }
        Boolean found = method.getBody().accept(new TreeScanner<Boolean, Void>() {
          @Override
          public Boolean visitVariable(VariableTree variable, Void unused) {
            return variable.getName().contentEquals(id.getName())
                && variable.getInitializer() != null
                && createsVirtualExecutor(variable.getInitializer());
          }

          @Override
          public Boolean reduce(@Nullable Boolean a, @Nullable Boolean b) {
            return Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b);
          }
        }, null);
        return Boolean.TRUE.equals(found);
      }

      private @Nullable MethodTree enclosingMethod() {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          if (p.getLeaf() instanceof MethodTree method) {
            return method;
          }
          if (p.getLeaf() instanceof ClassTree) {
            return null;
          }
        }
        return null;
      }
    };
  }

  private static boolean createsVirtualExecutor(ExpressionTree expression) {
    String text = expression.toString();
    return text.contains("newVirtualThreadPerTaskExecutor")
        || text.contains("newThreadPerTaskExecutor") && text.contains("ofVirtual");
  }

  /** ThreadLocal get/set/remove in the task body itself, not in nested lambdas or classes. */
  private static void reportAccesses(TreePath body, RuleContext ctx) {
    new TreePathScanner<Void, Void>() {
      @Override
      public @Nullable Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && ACCESS.contains(select.getIdentifier().toString())
            && ctx.isSubtypeOf(ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression())),
                "java.lang.ThreadLocal")) {
          ctx.report(node, "ThreadLocal." + select.getIdentifier() + " in a virtual-thread task: "
              + "each virtual thread starts with an empty copy that dies with the task, so a "
              + "ThreadLocal cache is never reused. Use ScopedValue for per-task context, or a "
              + "shared thread-safe object.");
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
    }.scan(body, null);
  }
}
