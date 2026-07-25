package io.codekoll.rules.numeric;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
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
 * CK-COMPARE-SUBTRACT: {@code return a - b;} as the body of {@code compareTo}/{@code compare}
 * overflows for large-magnitude ints, breaking the sort contract (a &lt; b but a - b &gt; 0).
 */
public final class CompareSubtractRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-COMPARE-SUBTRACT");
  private static final Set<String> COMPARATOR_METHODS = Set.of("compareTo", "compare");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.NUMERIC;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Comparator implemented by int subtraction (overflow breaks ordering)";
  }

  @Override
  public String explanation() {
    return "a - b overflows when the operands are far apart: Integer.MIN_VALUE - 1 wraps to "
        + "a POSITIVE number, so the comparator reports the wrong order. Sorting becomes "
        + "unstable or throws 'Comparison method violates its general contract!' — but only "
        + "on data with large values, typically in production.";
  }

  @Override
  public String fix() {
    return "Use Integer.compare(a, b) / Long.compare(a, b) — same speed, no overflow.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitReturn(ReturnTree node, RuleContext ctx) {
        if (node.getExpression() != null
            && NullFacts.unwrap(node.getExpression()) instanceof BinaryTree binary
            && binary.getKind() == Tree.Kind.MINUS
            && isIntOrLong(binary.getLeftOperand(), ctx)
            && insideComparatorMethod()) {
          ctx.report(node, "Subtraction-based comparison overflows for large values, "
              + "breaking the sort contract. Use Integer.compare(a, b).");
        }
        return super.visitReturn(node, ctx);
      }

      private boolean isIntOrLong(com.sun.source.tree.ExpressionTree operand,
          RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), operand));
        if (type == null) {
          return false;
        }
        if (type.getKind() == TypeKind.INT || type.getKind() == TypeKind.LONG) {
          return true;
        }
        // Boxed operands unbox in the subtraction and overflow identically.
        String name = ctx.qualifiedNameOf(type);
        return "java.lang.Integer".equals(name) || "java.lang.Long".equals(name);
      }

      private boolean insideComparatorMethod() {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          Tree leaf = p.getLeaf();
          if (leaf instanceof LambdaExpressionTree) {
            // Lambda comparators ((a, b) -> a - b) are covered when passed to sort/comparing;
            // v1 flags method bodies only — lambda detection needs the target type.
            return false;
          }
          if (leaf instanceof MethodTree method) {
            return COMPARATOR_METHODS.contains(method.getName().toString());
          }
        }
        return false;
      }
    };
  }
}
