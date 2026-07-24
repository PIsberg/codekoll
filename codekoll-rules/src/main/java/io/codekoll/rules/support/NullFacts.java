package io.codekoll.rules.support;

import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Null-fact utilities over boolean expressions: recognizing {@code x == null} /
 * {@code x != null} facts on simple identifiers, and finding dereferences of an identifier.
 * Built for CK-IMPOSSIBLE-COND; reused by the nullness pack (CK-NON-SHORT-CIRCUIT, …).
 *
 * <p>Facts are tracked for simple identifiers only (locals/parameters) — member selects and
 * anything a method call could change are conservatively out of scope.
 */
public final class NullFacts {

  private NullFacts() {}

  /** {@code x == null} → NULL fact; {@code x != null} → NONNULL fact. */
  public enum Kind { NULL, NONNULL }

  /** A nullness fact about identifier {@code name}. */
  public record Fact(String name, Kind kind) {}

  /** Strips parentheses. */
  public static ExpressionTree unwrap(ExpressionTree expr) {
    ExpressionTree e = expr;
    while (e instanceof ParenthesizedTree p) {
      e = p.getExpression();
    }
    return e;
  }

  /**
   * Recognizes {@code x == null} / {@code null == x} / {@code x != null} on a simple
   * identifier; returns the fact it establishes when the comparison is TRUE, else null.
   */
  public static @Nullable Fact factOf(ExpressionTree expr) {
    ExpressionTree e = unwrap(expr);
    if (!(e instanceof BinaryTree binary)) {
      return null;
    }
    boolean equal = binary.getKind() == Tree.Kind.EQUAL_TO;
    if (!equal && binary.getKind() != Tree.Kind.NOT_EQUAL_TO) {
      return null;
    }
    ExpressionTree left = unwrap(binary.getLeftOperand());
    ExpressionTree right = unwrap(binary.getRightOperand());
    String name = null;
    if (left.getKind() == Tree.Kind.NULL_LITERAL && right instanceof IdentifierTree id) {
      name = id.getName().toString();
    } else if (right.getKind() == Tree.Kind.NULL_LITERAL && left instanceof IdentifierTree id) {
      name = id.getName().toString();
    }
    if (name == null) {
      return null;
    }
    return new Fact(name, equal ? Kind.NULL : Kind.NONNULL);
  }

  /** All identifiers dereferenced anywhere inside {@code expr}: {@code x.m()}, {@code x.f}, {@code x[i]}. */
  public static List<String> dereferencedIdentifiers(ExpressionTree expr) {
    List<String> result = new ArrayList<>();
    expr.accept(new TreeScanner<Void, Void>() {
      @Override
      public Void visitMemberSelect(MemberSelectTree node, Void unused) {
        addIfIdentifier(node.getExpression());
        return super.visitMemberSelect(node, unused);
      }

      @Override
      public Void visitArrayAccess(ArrayAccessTree node, Void unused) {
        addIfIdentifier(node.getExpression());
        return super.visitArrayAccess(node, unused);
      }

      private void addIfIdentifier(ExpressionTree receiver) {
        if (unwrap(receiver) instanceof IdentifierTree id) {
          result.add(id.getName().toString());
        }
      }
    }, null);
    return result;
  }

  /** True when {@code expr} contains any method invocation (conservative fact invalidation). */
  public static boolean containsMethodCall(ExpressionTree expr) {
    Boolean found = expr.accept(new TreeScanner<Boolean, Void>() {
      @Override
      public Boolean visitMethodInvocation(MethodInvocationTree node, Void unused) {
        return true;
      }

      @Override
      public Boolean reduce(@Nullable Boolean a, @Nullable Boolean b) {
        return Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b);
      }
    }, null);
    return Boolean.TRUE.equals(found);
  }

  /** Flattens nested {@code &&} (or {@code ||}) chains into operand order. */
  public static List<ExpressionTree> flatten(ExpressionTree expr, Tree.Kind operator) {
    List<ExpressionTree> operands = new ArrayList<>();
    collect(unwrap(expr), operator, operands);
    return operands;
  }

  private static void collect(ExpressionTree expr, Tree.Kind operator,
      List<ExpressionTree> out) {
    if (expr.getKind() == operator && expr instanceof BinaryTree binary) {
      collect(unwrap(binary.getLeftOperand()), operator, out);
      collect(unwrap(binary.getRightOperand()), operator, out);
    } else {
      out.add(expr);
    }
  }
}
