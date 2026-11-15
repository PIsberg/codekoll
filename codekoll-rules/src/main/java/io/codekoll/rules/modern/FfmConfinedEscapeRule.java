package io.codekoll.rules.modern;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import org.jspecify.annotations.Nullable;

/**
 * CK-FFM-CONFINED-ESCAPE: a confined {@code Arena}, or a segment allocated from it, used in a
 * lambda that runs on another thread, where every access throws {@code WrongThreadException}.
 */
public final class FfmConfinedEscapeRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-FFM-CONFINED-ESCAPE");

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
    return "Confined Arena or its segment used on another thread";
  }

  @Override
  public String explanation() {
    return "Arena.ofConfined() binds the arena and every segment allocated from it to the thread "
        + "that created it. A lambda handed to a Thread, an executor or an *Async stage runs on a "
        + "different thread, and every access it makes to that memory (allocate, get, set, copy) "
        + "throws WrongThreadException. It compiles, and a unit test that never runs the task "
        + "on another thread passes.";
  }

  @Override
  public String fix() {
    return "Use Arena.ofShared() for memory that crosses threads, or create the confined arena "
        + "inside the task that uses it.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public @Nullable Void visitMethod(MethodTree node, RuleContext ctx) {
        if (node.getBody() != null) {
          analyze(new TreePath(getCurrentPath(), node.getBody()), ctx);
        }
        return super.visitMethod(node, ctx);
      }
    };
  }

  private static void analyze(TreePath body, RuleContext ctx) {
    Set<Element> confined = new HashSet<>();
    new TreePathScanner<Void, Void>() {
      @Override
      public @Nullable Void visitVariable(VariableTree node, Void unused) {
        ExpressionTree init = node.getInitializer();
        @Nullable Element variable = ctx.trees().getElement(getCurrentPath());
        if (init != null && variable != null) {
          TreePath initPath = new TreePath(getCurrentPath(), init);
          if (Ffm.isArenaFactory(initPath, ctx, Set.of("ofConfined"))
              || allocatedFrom(init, initPath, confined, ctx)) {
            confined.add(variable);
          }
        }
        return super.visitVariable(node, unused);
      }

      // An arena created inside a task belongs to the task's thread, not this method's.
      @Override
      public @Nullable Void visitLambdaExpression(LambdaExpressionTree node, Void unused) {
        return null;
      }

      @Override
      public @Nullable Void visitClass(ClassTree node, Void unused) {
        return null;
      }
    }.scan(body, null);
    if (confined.isEmpty()) {
      return;
    }
    new TreePathScanner<Void, Void>() {
      @Override
      public @Nullable Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        if (runsElsewhere(getCurrentPath(), ctx)) {
          check(node.getArguments(), getCurrentPath(), confined, ctx);
        }
        return super.visitMethodInvocation(node, unused);
      }

      @Override
      public @Nullable Void visitNewClass(NewClassTree node, Void unused) {
        if (ctx.isSubtypeOf(ctx.typeOf(getCurrentPath()), "java.lang.Thread")) {
          check(node.getArguments(), getCurrentPath(), confined, ctx);
        }
        return super.visitNewClass(node, unused);
      }
    }.scan(body, null);
  }

  /** A segment-typed initializer whose receiver chain starts at a confined arena or segment. */
  private static boolean allocatedFrom(ExpressionTree init, TreePath initPath,
      Set<Element> confined, RuleContext ctx) {
    if (!ctx.isSubtypeOf(ctx.typeOf(initPath), Ffm.SEGMENT)) {
      return false;
    }
    ExpressionTree current = Ffm.unwrap(init);
    while (current instanceof MethodInvocationTree call
        && call.getMethodSelect() instanceof MemberSelectTree select) {
      current = Ffm.unwrap(select.getExpression());
    }
    if (current instanceof IdentifierTree id) {
      @Nullable Element root = ctx.trees().getElement(new TreePath(initPath, id));
      return root != null && confined.contains(root);
    }
    return false;
  }

  /** Whether the call hands its lambda arguments to another thread. */
  private static boolean runsElsewhere(TreePath call, RuleContext ctx) {
    @Nullable Element element = ctx.trees().getElement(call);
    if (!(element instanceof ExecutableElement method)) {
      return false;
    }
    String name = method.getSimpleName().toString();
    var owner = method.getEnclosingElement().asType();
    return ctx.isSubtypeOf(owner, "java.util.concurrent.Executor")
        || ctx.isSubtypeOf(owner, "java.lang.Thread.Builder")
            && ("start".equals(name) || "unstarted".equals(name))
        || "java.lang.Thread".equals(ctx.qualifiedNameOf(owner))
            && "startVirtualThread".equals(name)
        || ctx.isSubtypeOf(owner, "java.util.concurrent.CompletionStage") && name.endsWith("Async");
  }

  private static void check(List<? extends ExpressionTree> args, TreePath call,
      Set<Element> confined, RuleContext ctx) {
    for (ExpressionTree arg : args) {
      if (arg instanceof LambdaExpressionTree lambda) {
        Set<Element> reported = new HashSet<>();
        new TreePathScanner<Void, Void>() {
          @Override
          public @Nullable Void visitIdentifier(IdentifierTree node, Void unused) {
            @Nullable Element element = ctx.trees().getElement(getCurrentPath());
            if (element != null && confined.contains(element) && reported.add(element)) {
              ctx.report(node, "'" + node.getName() + "' belongs to a confined Arena, and this "
                  + "lambda runs on another thread: every access throws WrongThreadException. "
                  + "Use Arena.ofShared(), or create the arena inside the task.");
            }
            return super.visitIdentifier(node, unused);
          }
        }.scan(new TreePath(new TreePath(call, lambda), lambda.getBody()), null);
      }
    }
  }
}
