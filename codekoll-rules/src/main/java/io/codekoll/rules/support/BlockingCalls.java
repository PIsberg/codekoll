package io.codekoll.rules.support;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import org.jspecify.annotations.Nullable;

/**
 * Finds calls that block, judged by what the attributed callee declares it throws. A method that
 * declares {@code InterruptedException} waits for something; one that declares
 * {@code IOException} or {@code SQLException} does I/O. Used by the rules about work that must
 * not block (the common pool, a monitor on a virtual thread) and work that must not swallow the
 * interrupt.
 */
public final class BlockingCalls {

  public static final String INTERRUPTED = "java.lang.InterruptedException";
  public static final String IO = "java.io.IOException";
  public static final String SQL = "java.sql.SQLException";

  /** Waiting and I/O: what blocks a thread. */
  public static final Set<String> BLOCKING = Set.of(INTERRUPTED, IO, SQL);

  private BlockingCalls() {}

  /**
   * Method calls and constructor invocations under {@code root} whose callee declares a subtype
   * of one of {@code thrown}. Nested lambda and class bodies are skipped: they run later, or on
   * another thread, not as part of {@code root}.
   */
  public static List<TreePath> declaring(TreePath root, RuleContext ctx, Set<String> thrown) {
    List<TreePath> found = new ArrayList<>();
    new TreePathScanner<Void, Void>() {
      @Override
      public @Nullable Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        record(getCurrentPath());
        return super.visitMethodInvocation(node, unused);
      }

      @Override
      public @Nullable Void visitNewClass(NewClassTree node, Void unused) {
        record(getCurrentPath());
        return super.visitNewClass(node, unused);
      }

      @Override
      public @Nullable Void visitLambdaExpression(LambdaExpressionTree node, Void unused) {
        return null;
      }

      @Override
      public @Nullable Void visitClass(ClassTree node, Void unused) {
        return null;
      }

      private void record(TreePath call) {
        if (declares(call, ctx, thrown)) {
          found.add(call);
        }
      }
    }.scan(root, null);
    return found;
  }

  /** Whether the call at {@code call} resolves to a method declaring one of {@code thrown}. */
  public static boolean declares(TreePath call, RuleContext ctx, Set<String> thrown) {
    @Nullable Element element = ctx.trees().getElement(call);
    return element instanceof ExecutableElement method && method.getThrownTypes().stream()
        .anyMatch(type -> thrown.stream().anyMatch(fqn -> ctx.isSubtypeOf(type, fqn)));
  }
}
