package io.codekoll.rules.deadcode;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import io.codekoll.rules.support.NullFacts;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates conditions built only from literals. Named constants, calls, strings and casts give
 * {@link Truth#UNKNOWN}, so constant-flag guards never produce findings.
 */
final class LiteralConditions {

  /** Outcome of evaluating a condition. */
  enum Truth { TRUE, FALSE, UNKNOWN }

  private LiteralConditions() {}

  static Truth valueOf(ExpressionTree condition) {
    ExpressionTree e = NullFacts.unwrap(condition);
    if (e instanceof LiteralTree lit && lit.getValue() instanceof Boolean b) {
      return of(b);
    }
    if (e instanceof UnaryTree u && u.getKind() == Tree.Kind.LOGICAL_COMPLEMENT) {
      return switch (valueOf(u.getExpression())) {
        case TRUE -> Truth.FALSE;
        case FALSE -> Truth.TRUE;
        case UNKNOWN -> Truth.UNKNOWN;
      };
    }
    if (e instanceof BinaryTree b) {
      return binary(b);
    }
    return Truth.UNKNOWN;
  }

  private static Truth binary(BinaryTree b) {
    Tree.Kind k = b.getKind();
    switch (k) {
      case CONDITIONAL_AND, CONDITIONAL_OR, XOR, EQUAL_TO, NOT_EQUAL_TO, LESS_THAN,
          LESS_THAN_EQUAL, GREATER_THAN, GREATER_THAN_EQUAL -> { }
      default -> {
        return Truth.UNKNOWN;
      }
    }
    Truth l = valueOf(b.getLeftOperand());
    Truth r = valueOf(b.getRightOperand());
    if (l != Truth.UNKNOWN && r != Truth.UNKNOWN) {
      boolean x = l == Truth.TRUE;
      boolean y = r == Truth.TRUE;
      return switch (k) {
        case CONDITIONAL_AND -> of(x && y);
        case CONDITIONAL_OR -> of(x || y);
        case XOR, NOT_EQUAL_TO -> of(x != y);
        case EQUAL_TO -> of(x == y);
        default -> Truth.UNKNOWN;
      };
    }
    Number lv = number(b.getLeftOperand());
    Number rv = number(b.getRightOperand());
    if (lv == null || rv == null) {
      return Truth.UNKNOWN;
    }
    int cmp;
    if (lv instanceof Long a && rv instanceof Long c) {
      cmp = Long.compare(a, c);
    } else {
      double a = lv.doubleValue();
      double c = rv.doubleValue();
      if (Double.isNaN(a) || Double.isNaN(c)) {
        return Truth.UNKNOWN;
      }
      cmp = a == c ? 0 : Double.compare(a, c);
    }
    return switch (k) {
      case EQUAL_TO -> of(cmp == 0);
      case NOT_EQUAL_TO -> of(cmp != 0);
      case LESS_THAN -> of(cmp < 0);
      case LESS_THAN_EQUAL -> of(cmp <= 0);
      case GREATER_THAN -> of(cmp > 0);
      case GREATER_THAN_EQUAL -> of(cmp >= 0);
      default -> Truth.UNKNOWN;
    };
  }

  /** Long for int/long/char literals, Double for float/double, else null. */
  private static @Nullable Number number(ExpressionTree expr) {
    ExpressionTree e = NullFacts.unwrap(expr);
    if (!(e instanceof LiteralTree lit)) {
      return null;
    }
    Object v = lit.getValue();
    if (v instanceof Character c) {
      return (long) c;
    }
    if (v instanceof Integer || v instanceof Long) {
      return ((Number) v).longValue();
    }
    if (v instanceof Float || v instanceof Double) {
      return ((Number) v).doubleValue();
    }
    return null;
  }

  private static Truth of(boolean b) {
    return b ? Truth.TRUE : Truth.FALSE;
  }
}
