package io.codekoll.rules.concurrency;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import io.codekoll.rules.support.RuleContext;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Shared shape checks for the rules that track the thread interrupt signal
 * (CK-INTERRUPT-NOT-RESTORED, CK-CANCEL-SWALLOWED).
 */
final class InterruptSignals {

  static final String INTERRUPTED = "java.lang.InterruptedException";
  static final String CANCELLED = "java.util.concurrent.CancellationException";

  private InterruptSignals() {}

  /** The type trees a catch clause names: the alternatives of a multi-catch, or the one type. */
  static List<TreePath> caughtTypes(TreePath catchPath) {
    CatchTree node = (CatchTree) catchPath.getLeaf();
    TreePath param = new TreePath(catchPath, node.getParameter());
    Tree type = node.getParameter().getType();
    TreePath typePath = new TreePath(param, type);
    if (type instanceof UnionTypeTree union) {
      return union.getTypeAlternatives().stream().map(alt -> new TreePath(typePath, alt)).toList();
    }
    return List.of(typePath);
  }

  /** Whether the catch clause at {@code catchPath} names {@code fqn} or a subtype of it. */
  static boolean catches(TreePath catchPath, String fqn, RuleContext ctx) {
    return caughtTypes(catchPath).stream()
        .anyMatch(type -> ctx.isSubtypeOf(ctx.typeOf(type), fqn));
  }

  /** Any no-argument {@code interrupt()} call, the {@code currentThread().interrupt()} idiom. */
  static boolean callsInterrupt(Tree tree) {
    return Boolean.TRUE.equals(tree.accept(new AnyScanner() {
      @Override
      public Boolean visitMethodInvocation(MethodInvocationTree node, Void unused) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("interrupt")
            && node.getArguments().isEmpty()) {
          return Boolean.TRUE;
        }
        return super.visitMethodInvocation(node, unused);
      }
    }, null));
  }

  /** Any {@code throw} statement at all. */
  static boolean containsThrow(Tree tree) {
    return Boolean.TRUE.equals(tree.accept(new AnyScanner() {
      @Override
      public Boolean visitThrow(ThrowTree node, Void unused) {
        return Boolean.TRUE;
      }
    }, null));
  }

  /** {@code throw name;} for the given variable. */
  static boolean rethrows(Tree tree, String name) {
    return Boolean.TRUE.equals(tree.accept(new AnyScanner() {
      @Override
      public Boolean visitThrow(ThrowTree node, Void unused) {
        return node.getExpression() instanceof IdentifierTree id && id.getName().contentEquals(name);
      }
    }, null));
  }

  /** An {@code instanceof} test against InterruptedException or CancellationException. */
  static boolean testsForCancellation(Tree tree) {
    return Boolean.TRUE.equals(tree.accept(new AnyScanner() {
      @Override
      public Boolean visitInstanceOf(InstanceOfTree node, Void unused) {
        String test = node.toString();
        return test.contains("InterruptedException") || test.contains("CancellationException");
      }
    }, null));
  }

  /**
   * Whether the catch at {@code catchPath} sits at the end of a thread entry point ({@code run()},
   * {@code main}, a {@code Runnable} lambda) where nothing is left to read the interrupt flag: its
   * block ends with {@code return}, ends with {@code break} out of the body's final loop, or its
   * {@code try} is the body's last statement.
   */
  @SuppressWarnings("PMD.CompareObjectsWithEquals") // AST node identity, not value equality
  static boolean atThreadEntryEnd(TreePath catchPath, RuleContext ctx) {
    @Nullable BlockTree body = entryBody(catchPath, ctx);
    if (body == null || body.getStatements().isEmpty()) {
      return false;
    }
    StatementTree last = body.getStatements().getLast();
    TreePath tryPath = catchPath.getParentPath();
    if (tryPath.getLeaf() == last) {
      return true;
    }
    List<? extends StatementTree> handler = ((CatchTree) catchPath.getLeaf()).getBlock()
        .getStatements();
    if (handler.isEmpty()) {
      return false;
    }
    StatementTree end = handler.getLast();
    if (end instanceof ReturnTree) {
      return true;
    }
    return end instanceof BreakTree br && br.getLabel() == null
        && innermostBreakTarget(tryPath) == last;
  }

  private static @Nullable Tree innermostBreakTarget(TreePath from) {
    for (TreePath p = from; p != null; p = p.getParentPath()) {
      Tree leaf = p.getLeaf();
      if (leaf instanceof WhileLoopTree || leaf instanceof DoWhileLoopTree
          || leaf instanceof ForLoopTree || leaf instanceof EnhancedForLoopTree
          || leaf instanceof SwitchTree) {
        return leaf;
      }
      if (leaf instanceof MethodTree || leaf instanceof LambdaExpressionTree) {
        return null;
      }
    }
    return null;
  }

  private static @Nullable BlockTree entryBody(TreePath from, RuleContext ctx) {
    for (TreePath p = from; p != null; p = p.getParentPath()) {
      Tree leaf = p.getLeaf();
      if (leaf instanceof ClassTree) {
        return null;
      }
      if (leaf instanceof LambdaExpressionTree lambda) {
        boolean runnable = "java.lang.Runnable".equals(ctx.qualifiedNameOf(ctx.typeOf(p)));
        return runnable && lambda.getBody() instanceof BlockTree block ? block : null;
      }
      if (leaf instanceof MethodTree method) {
        return isEntryPoint(method) ? method.getBody() : null;
      }
    }
    return null;
  }

  private static boolean isEntryPoint(MethodTree method) {
    String name = method.getName().toString();
    boolean isVoid = method.getReturnType() != null
        && "void".equals(method.getReturnType().toString());
    if (!isVoid) {
      return false;
    }
    if ("run".equals(name)) {
      return method.getParameters().isEmpty();
    }
    // Java 25 launches instance and no-argument main methods too (JEP 512).
    return "main".equals(name) && method.getParameters().size() <= 1;
  }

  /** A scanner answering "does any node match", OR-reducing child results. */
  private abstract static class AnyScanner extends TreeScanner<Boolean, Void> {
    @Override
    public Boolean reduce(@Nullable Boolean a, @Nullable Boolean b) {
      return Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b);
    }
  }
}
