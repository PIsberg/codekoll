package io.codekoll.rules.correctness;

import com.sun.source.tree.BinaryTree;
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
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * CK-SELF-COMPARE: comparing an expression with itself — {@code x == x}, {@code x.equals(x)},
 * {@code x.compareTo(x)} — has a constant result; usually a copy-paste slip where one side
 * was meant to be another variable. Exemption: {@code x != x} on float/double (NaN idiom).
 */
public final class SelfCompareRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-SELF-COMPARE");
  private static final Set<String> COMPARE_METHODS = Set.of("equals", "compareTo");

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
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Expression compared with itself (constant result)";
  }

  @Override
  public String explanation() {
    return "Both operands are the same variable, so the comparison always yields the same "
        + "answer — the condition it guards is meaningless. Nearly always a copy-paste slip "
        + "where one operand was meant to be a different variable (a.x == a.x instead of "
        + "a.x == b.x).";
  }

  @Override
  public String fix() {
    return "Compare against the intended other operand. For a float/double NaN check, use "
        + "Double.isNaN(x) instead of x != x.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitBinary(BinaryTree node, RuleContext ctx) {
        if ((node.getKind() == Tree.Kind.EQUAL_TO || node.getKind() == Tree.Kind.NOT_EQUAL_TO
            || node.getKind() == Tree.Kind.LESS_THAN
            || node.getKind() == Tree.Kind.GREATER_THAN
            || node.getKind() == Tree.Kind.LESS_THAN_EQUAL
            || node.getKind() == Tree.Kind.GREATER_THAN_EQUAL)
            && sameSimpleOperand(node.getLeftOperand(), node.getRightOperand())
            && !isNanIdiom(node, ctx)) {
          ctx.report(node, "Both operands are '" + NullFacts.unwrap(node.getLeftOperand())
              + "' — the result is constant. Compare against the intended other value.");
        }
        return super.visitBinary(node, ctx);
      }

      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 1
            && node.getMethodSelect() instanceof MemberSelectTree select
            && COMPARE_METHODS.contains(select.getIdentifier().toString())
            && sameSimpleOperand(select.getExpression(), node.getArguments().get(0))) {
          ctx.report(node, select.getIdentifier() + "() called with its own receiver — the "
              + "result is constant. Compare against the intended other value.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      /** Structural identity for side-effect-free operand shapes (identifiers, selects). */
      private boolean sameSimpleOperand(ExpressionTree left, ExpressionTree right) {
        ExpressionTree l = NullFacts.unwrap(left);
        ExpressionTree r = NullFacts.unwrap(right);
        if (l instanceof MethodInvocationTree || r instanceof MethodInvocationTree) {
          return false;
        }
        boolean simple = l instanceof com.sun.source.tree.IdentifierTree
            || l instanceof MemberSelectTree;
        return simple && l.toString().equals(r.toString());
      }

      private boolean isNanIdiom(BinaryTree node, RuleContext ctx) {
        if (node.getKind() != Tree.Kind.NOT_EQUAL_TO) {
          return false;
        }
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), node.getLeftOperand()));
        return type != null
            && (type.getKind() == TypeKind.DOUBLE || type.getKind() == TypeKind.FLOAT);
      }
    };
  }
}
