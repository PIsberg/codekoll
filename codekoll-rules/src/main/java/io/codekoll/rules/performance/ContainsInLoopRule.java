package io.codekoll.rules.performance;

import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
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
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeMirror;

/**
 * CK-CONTAINS-IN-LOOP: {@code list.contains(...)}/{@code indexOf(...)} on a List declared
 * outside a loop, invoked inside it — O(n*m) scanning. Suggest a HashSet.
 */
public final class ContainsInLoopRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-CONTAINS-IN-LOOP");

  private static final Set<String> LINEAR_METHODS = Set.of("contains", "indexOf");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.PERFORMANCE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "List.contains/indexOf called inside a loop (O(n*m))";
  }

  @Override
  public String explanation() {
    return "List.contains scans the whole list — O(n). Inside a loop of m iterations that "
        + "is O(n*m): fine for tiny lists, quadratic blowup for large ones. The 'filter out "
        + "items already seen' loop that ran in milliseconds on test data takes minutes in "
        + "production.";
  }

  @Override
  public String fix() {
    return "Build a HashSet once before the loop and use its O(1) contains — or use "
        + "Collection operations (removeAll / retainAll) where they fit.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 1
            && node.getMethodSelect() instanceof MemberSelectTree select
            && LINEAR_METHODS.contains(select.getIdentifier().toString())
            && isList(select.getExpression(), ctx)
            && insideLoop()
            && isDeclaredOutsideEnclosingLoop(select.getExpression(), ctx)) {
          ctx.report(node, select.getIdentifier() + " on a List inside a loop is O(n*m). "
              + "Build a HashSet before the loop for O(1) lookups.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isList(ExpressionTree receiver, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), receiver));
        return ctx.isSubtypeOf(type, "java.util.List");
      }

      private boolean insideLoop() {
        return enclosingLoop() != null;
      }

      private @org.jspecify.annotations.Nullable TreePath enclosingLoop() {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          Tree leaf = p.getLeaf();
          if (leaf instanceof com.sun.source.tree.MethodTree) {
            return null;
          }
          if (leaf instanceof WhileLoopTree || leaf instanceof DoWhileLoopTree
              || leaf instanceof ForLoopTree || leaf instanceof EnhancedForLoopTree) {
            return p;
          }
        }
        return null;
      }

      /** The receiver is a field or a local/param declared outside the enclosing loop. */
      private boolean isDeclaredOutsideEnclosingLoop(ExpressionTree receiver, RuleContext ctx) {
        if (!(receiver instanceof IdentifierTree || receiver instanceof MemberSelectTree)) {
          return false;
        }
        Element symbol = ctx.trees().getElement(new TreePath(getCurrentPath(), receiver));
        if (symbol == null || symbol.getKind() == ElementKind.FIELD
            || symbol.getKind() == ElementKind.PARAMETER) {
          return true;
        }
        // Local variable: declared outside iff the loop is not an ancestor of the decl.
        Tree declaration = ctx.trees().getTree(symbol);
        TreePath loop = enclosingLoop();
        if (loop == null || declaration == null) {
          return true;
        }
        TreePath declPath = ctx.trees().getPath(symbol);
        return declPath == null || !isAncestor(loop, declPath);
      }

      @SuppressWarnings("PMD.CompareObjectsWithEquals")
      private boolean isAncestor(TreePath ancestor, TreePath descendant) {
        for (TreePath p = descendant; p != null; p = p.getParentPath()) {
          if (p.getLeaf() == ancestor.getLeaf()) {
            return true;
          }
        }
        return false;
      }
    };
  }
}
