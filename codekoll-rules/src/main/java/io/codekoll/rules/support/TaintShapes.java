package io.codekoll.rules.support;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;

/**
 * Shared injection-shape helper: a string expression that concatenates literals with
 * non-constant parts (the SQL/command-injection shape). Used by CK-SQL-CONCAT and
 * CK-EXEC-CONCAT.
 */
public final class TaintShapes {

  private TaintShapes() {}

  /**
   * True when {@code expr} is a {@code +} concatenation that mixes at least one string
   * literal with at least one non-literal part — the classic injectable query/command
   * shape. Pure-literal concatenation is constant and safe.
   */
  public static boolean isLiteralPlusNonConstant(ExpressionTree expr) {
    ExpressionTree unwrapped = NullFacts.unwrap(expr);
    if (!(unwrapped instanceof BinaryTree) || unwrapped.getKind() != Tree.Kind.PLUS) {
      return false;
    }
    boolean[] seen = new boolean[2];  // [literal, nonConstant]
    collect(unwrapped, seen);
    return seen[0] && seen[1];
  }

  // seen[] is a 2-slot accumulator, not a varargs list.
  @SuppressWarnings("PMD.UseVarargs")
  private static void collect(ExpressionTree expr, boolean[] seen) {
    ExpressionTree e = NullFacts.unwrap(expr);
    if (e instanceof BinaryTree binary && e.getKind() == Tree.Kind.PLUS) {
      collect(binary.getLeftOperand(), seen);
      collect(binary.getRightOperand(), seen);
    } else if (e instanceof LiteralTree) {
      seen[0] = true;
    } else {
      seen[1] = true;
    }
  }
}
