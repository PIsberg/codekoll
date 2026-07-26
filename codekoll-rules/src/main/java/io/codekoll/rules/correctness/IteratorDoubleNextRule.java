package io.codekoll.rules.correctness;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import org.jspecify.annotations.Nullable;

/**
 * CK-ITERATOR-DOUBLE-NEXT: a loop guarded by {@code it.hasNext()} that calls {@code it.next()}
 * more than once per iteration. The guard promises one element; the body consumes two.
 */
public final class IteratorDoubleNextRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-ITERATOR-DOUBLE-NEXT");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.CORRECTNESS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Loop guarded by hasNext() calls next() twice in one iteration";
  }

  @Override
  public String explanation() {
    return "hasNext() promises exactly one more element. A body that calls next() twice "
        + "consumes two, so the guard no longer matches what the loop does: on an input with "
        + "an odd number of remaining elements the second next() throws "
        + "NoSuchElementException. It survives every even-sized fixture, which is why this "
        + "reaches production and then fails on the one record that has a trailing field.";
  }

  @Override
  public String fix() {
    return "Call next() once, store the element in a local, and use that local — or, for "
        + "deliberate pairwise consumption, guard the second read with its own "
        + "if (it.hasNext()).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitWhileLoop(WhileLoopTree node, RuleContext ctx) {
        checkLoop(node.getCondition(), node.getStatement(), ctx);
        return super.visitWhileLoop(node, ctx);
      }

      @Override
      public Void visitDoWhileLoop(DoWhileLoopTree node, RuleContext ctx) {
        checkLoop(node.getCondition(), node.getStatement(), ctx);
        return super.visitDoWhileLoop(node, ctx);
      }

      @Override
      public Void visitForLoop(ForLoopTree node, RuleContext ctx) {
        if (node.getCondition() != null) {
          checkLoop(node.getCondition(), node.getStatement(), ctx);
        }
        return super.visitForLoop(node, ctx);
      }

      private void checkLoop(ExpressionTree condition, StatementTree body, RuleContext ctx) {
        TreePath conditionPath = new TreePath(getCurrentPath(), condition);
        for (Element iterator : guardedIterators(conditionPath, ctx)) {
          reportSecondUnconditionalNext(iterator, body, ctx);
        }
      }

      /** Iterator variables the loop condition tests with {@code hasNext()}. */
      private Set<Element> guardedIterators(TreePath conditionPath, RuleContext ctx) {
        Set<Element> found = new LinkedHashSet<>();
        new TreePathScanner<Void, Void>() {
          @Override
          public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            Element receiver = receiverOfCall(node, "hasNext", getCurrentPath(), ctx);
            if (receiver != null && isIterator(receiver, ctx)) {
              found.add(receiver);
            }
            return super.visitMethodInvocation(node, unused);
          }
        }.scan(conditionPath, null);
        return found;
      }

      private void reportSecondUnconditionalNext(Element iterator, StatementTree body,
          RuleContext ctx) {
        List<TreePath> calls = unconditionalNextCalls(iterator, body, ctx);
        for (int i = 0; i < calls.size(); i++) {
          for (int j = i + 1; j < calls.size(); j++) {
            if (!mutuallyExclusive(calls.get(i), calls.get(j), body)) {
              ctx.report(calls.get(j).getLeaf(), "This loop's hasNext() guard promises one "
                  + "element but the body calls " + iterator.getSimpleName() + ".next() again — "
                  + "an odd number of remaining elements makes this throw "
                  + "NoSuchElementException. Read once into a local, or guard this read with "
                  + "its own if (" + iterator.getSimpleName() + ".hasNext()).");
              return;
            }
          }
        }
      }

      /**
       * {@code iterator.next()} calls in the loop body that no inner construct re-guards.
       * Nested loops, lambdas, local classes, switches, ternaries and {@code hasNext()}-guarded
       * ifs are not descended into: each could legitimately re-check before reading.
       */
      private List<TreePath> unconditionalNextCalls(Element iterator, StatementTree body,
          RuleContext ctx) {
        List<TreePath> calls = new ArrayList<>();
        new TreePathScanner<Void, Void>() {
          @Override
          public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            if (iterator.equals(receiverOfCall(node, "next", getCurrentPath(), ctx))) {
              calls.add(getCurrentPath());
            }
            return super.visitMethodInvocation(node, unused);
          }

          @Override
          public Void visitIf(IfTree node, Void unused) {
            TreePath conditionPath = new TreePath(getCurrentPath(), node.getCondition());
            return guardedIterators(conditionPath, ctx).contains(iterator)
                ? null : super.visitIf(node, unused);
          }

          @Override
          public Void visitWhileLoop(WhileLoopTree node, Void unused) {
            return null;
          }

          @Override
          public Void visitDoWhileLoop(DoWhileLoopTree node, Void unused) {
            return null;
          }

          @Override
          public Void visitForLoop(ForLoopTree node, Void unused) {
            return null;
          }

          @Override
          public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void unused) {
            return null;
          }

          @Override
          public Void visitSwitch(SwitchTree node, Void unused) {
            return null;
          }

          @Override
          public Void visitConditionalExpression(ConditionalExpressionTree node, Void unused) {
            return null;
          }

          @Override
          public Void visitLambdaExpression(LambdaExpressionTree node, Void unused) {
            return null;
          }

          @Override
          public Void visitClass(ClassTree node, Void unused) {
            return null;
          }
        }.scan(new TreePath(getCurrentPath(), body), null);
        return calls;
      }

      /**
       * True when the two calls sit in opposite branches of the same if, so only one runs.
       *
       * <p>Reference comparison is deliberate throughout: AST nodes are compared by identity,
       * since two structurally equal subtrees are still different places in the file.
       */
      @SuppressWarnings("PMD.CompareObjectsWithEquals") // AST node identity, not value equality
      private boolean mutuallyExclusive(TreePath first, TreePath second, StatementTree body) {
        for (TreePath a = first; a != null && a.getLeaf() != body; a = a.getParentPath()) {
          Tree parent = a.getParentPath() == null ? null : a.getParentPath().getLeaf();
          if (!(parent instanceof IfTree branch)) {
            continue;
          }
          boolean firstInThen = branch.getThenStatement() == a.getLeaf();
          for (TreePath b = second; b != null && b.getLeaf() != body; b = b.getParentPath()) {
            Tree otherParent = b.getParentPath() == null ? null : b.getParentPath().getLeaf();
            boolean secondInThen = branch.getThenStatement() == b.getLeaf();
            if (otherParent == branch && secondInThen != firstInThen) {
              return true;
            }
          }
        }
        return false;
      }

      /** Receiver element of {@code receiver.name()}, or null when the shape does not match. */
      private @Nullable Element receiverOfCall(MethodInvocationTree node, String name,
          TreePath path, RuleContext ctx) {
        if (!node.getArguments().isEmpty()
            || !(node.getMethodSelect() instanceof MemberSelectTree select)
            || !name.equals(select.getIdentifier().toString())) {
          return null;
        }
        return ctx.trees().getElement(new TreePath(path, select.getExpression()));
      }

      private boolean isIterator(Element element, RuleContext ctx) {
        return ctx.isSubtypeOf(element.asType(), "java.util.Iterator");
      }
    };
  }
}
