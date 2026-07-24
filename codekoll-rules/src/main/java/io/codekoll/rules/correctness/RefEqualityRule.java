package io.codekoll.rules.correctness;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * CK-REF-EQUALITY: {@code ==}/{@code !=} on String or boxed types compares references, not
 * contents. Exemptions: comparison with the {@code null} literal, comparison with a primitive
 * operand (unboxing makes it a value comparison), and the {@code this == obj} identity
 * fast-path inside an {@code equals} implementation.
 */
public final class RefEqualityRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-REF-EQUALITY");

  private static final Set<String> BOXED = Set.of(
      "java.lang.Integer", "java.lang.Long", "java.lang.Double", "java.lang.Float",
      "java.lang.Short", "java.lang.Byte", "java.lang.Character", "java.lang.Boolean");

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
    return "== or != on String/boxed types compares references, not values";
  }

  @Override
  public String explanation() {
    return "On objects, == compares memory addresses, not contents. Two equal Strings (or "
        + "boxed Integers outside the -128..127 cache) can be different objects, so the "
        + "comparison is false even when the values match — the classic works-in-test, "
        + "fails-in-production bug.";
  }

  @Override
  public String fix() {
    return "Use .equals(), or Objects.equals(a, b) when either side may be null. "
        + "(== stays correct for enums and for null checks.)";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitBinary(BinaryTree node, RuleContext ctx) {
        if ((node.getKind() == Tree.Kind.EQUAL_TO || node.getKind() == Tree.Kind.NOT_EQUAL_TO)
            && !isNullLiteral(node.getLeftOperand())
            && !isNullLiteral(node.getRightOperand())
            && !isThisIdentityFastPath(node)) {
          TypeMirror left = ctx.typeOf(new TreePath(getCurrentPath(), node.getLeftOperand()));
          TypeMirror right = ctx.typeOf(new TreePath(getCurrentPath(), node.getRightOperand()));
          if (isFlagged(left, right, ctx) || isFlagged(right, left, ctx)) {
            ctx.report(node, "== compares references, not contents. Use .equals() "
                + "(or Objects.equals() if either side may be null).");
          }
        }
        return super.visitBinary(node, ctx);
      }

      private boolean isFlagged(@Nullable TypeMirror candidate, @Nullable TypeMirror other,
          RuleContext ctx) {
        if (candidate == null || other == null || other.getKind().isPrimitive()) {
          return false;
        }
        String name = ctx.qualifiedNameOf(candidate);
        return "java.lang.String".equals(name) || BOXED.contains(name);
      }

      private boolean isThisIdentityFastPath(BinaryTree node) {
        if (!isThisKeyword(node.getLeftOperand()) && !isThisKeyword(node.getRightOperand())) {
          return false;
        }
        TreePath path = getCurrentPath();
        while (path != null) {
          if (path.getLeaf() instanceof MethodTree method) {
            return method.getName().contentEquals("equals")
                && method.getParameters().size() == 1;
          }
          path = path.getParentPath();
        }
        return false;
      }

      private boolean isThisKeyword(ExpressionTree expr) {
        return expr.getKind() == Tree.Kind.IDENTIFIER && "this".equals(expr.toString());
      }

      private boolean isNullLiteral(ExpressionTree expr) {
        return expr.getKind() == Tree.Kind.NULL_LITERAL;
      }
    };
  }
}
