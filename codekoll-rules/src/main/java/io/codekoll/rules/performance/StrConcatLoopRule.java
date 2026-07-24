package io.codekoll.rules.performance;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * CK-STR-CONCAT-LOOP: appending to a String variable inside a loop re-copies the whole
 * accumulated string every iteration — O(n²). Fires on {@code s += …}, {@code s = s + …} and
 * {@code s = s.concat(…)} where {@code s} is a String declared <b>outside</b> the loop.
 * Nothing inside lambdas is flagged (loop context does not reliably transfer).
 */
public final class StrConcatLoopRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-STR-CONCAT-LOOP");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "String concatenation accumulating across loop iterations";
  }

  @Override
  public String explanation() {
    return "Strings are immutable, so s += x inside a loop allocates a brand-new string and "
        + "copies every character accumulated so far — on every single iteration. Total "
        + "work grows quadratically with the number of iterations: fine at 100 elements, "
        + "seconds of CPU and GC pressure at 100 000.";
  }

  @Override
  public String fix() {
    return "Accumulate in a StringBuilder inside the loop and call toString() once after it "
        + "(or use String.join / Collectors.joining).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {

      @Override
      public Void visitCompoundAssignment(CompoundAssignmentTree node, RuleContext ctx) {
        if (node.getKind() == Tree.Kind.PLUS_ASSIGNMENT
            && isStringVarDeclaredOutsideEnclosingLoop(node.getVariable(), ctx)) {
          ctx.report(node, "String concatenation in a loop is O(n^2). Build with a "
              + "StringBuilder and call toString() after the loop.");
        }
        return super.visitCompoundAssignment(node, ctx);
      }

      @Override
      public Void visitAssignment(AssignmentTree node, RuleContext ctx) {
        if (isSelfConcat(node) && isStringVarDeclaredOutsideEnclosingLoop(node.getVariable(),
            ctx)) {
          ctx.report(node, "String concatenation in a loop is O(n^2). Build with a "
              + "StringBuilder and call toString() after the loop.");
        }
        return super.visitAssignment(node, ctx);
      }

      /** s = s + … (either operand) or s = s.concat(…). */
      private boolean isSelfConcat(AssignmentTree node) {
        String target = node.getVariable().toString();
        ExpressionTree value = node.getExpression();
        if (value instanceof BinaryTree binary && value.getKind() == Tree.Kind.PLUS) {
          return containsIdentifier(binary, target);
        }
        return value.toString().startsWith(target + ".concat(");
      }

      private boolean containsIdentifier(BinaryTree binary, String name) {
        return binary.getLeftOperand().toString().equals(name)
            || binary.getRightOperand().toString().equals(name)
            || (binary.getLeftOperand() instanceof BinaryTree nested
                && containsIdentifier(nested, name));
      }

      private boolean isStringVarDeclaredOutsideEnclosingLoop(ExpressionTree variable,
          RuleContext ctx) {
        if (!(variable instanceof IdentifierTree)) {
          return false;
        }
        TreePath varPath = new TreePath(getCurrentPath(), variable);
        TypeMirror type = ctx.typeOf(varPath);
        if (!"java.lang.String".equals(ctx.qualifiedNameOf(type))) {
          return false;
        }
        Element symbol = ctx.trees().getElement(varPath);
        if (symbol == null || symbol.getKind() == ElementKind.FIELD) {
          return enclosingLoop(getCurrentPath()) != null;
        }
        TreePath loop = enclosingLoop(getCurrentPath());
        if (loop == null) {
          return false;
        }
        TreePath declaration = declarationPath(symbol, ctx);
        // Declared outside the loop iff the loop is NOT an ancestor of the declaration.
        return declaration == null || !isAncestor(loop, declaration);
      }

      private @Nullable TreePath enclosingLoop(TreePath path) {
        for (TreePath p = path; p != null; p = p.getParentPath()) {
          Tree leaf = p.getLeaf();
          if (leaf instanceof LambdaExpressionTree) {
            return null;
          }
          if (leaf instanceof ForLoopTree || leaf instanceof EnhancedForLoopTree
              || leaf instanceof WhileLoopTree || leaf instanceof DoWhileLoopTree) {
            return p;
          }
        }
        return null;
      }

      private @Nullable TreePath declarationPath(Element symbol, RuleContext ctx) {
        Tree tree = ctx.trees().getTree(symbol);
        return tree instanceof VariableTree
            ? ctx.trees().getPath(symbol)
            : null;
      }

      // AST nodes have no value equality; node identity IS the correct comparison here.
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
