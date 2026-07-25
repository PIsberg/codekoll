package io.codekoll.rules.correctness;

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
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * CK-TOSTRING-ARRAY: an array where an implicit toString() happens — string concatenation,
 * println, %s — prints {@code [Ljava.lang.String;@1a2b3c} instead of the contents.
 */
public final class ToStringArrayRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-TOSTRING-ARRAY");

  private static final Set<String> PRINT_METHODS =
      Set.of("println", "print", "valueOf");

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
    return "Array where an implicit toString() is triggered";
  }

  @Override
  public String explanation() {
    return "Arrays inherit Object.toString, which yields '[Ljava.lang.String;@1a2b3c' — "
        + "type sigil plus identity hash, never the contents. String concatenation, "
        + "println and %s all trigger it silently, so the log line meant to show the data "
        + "shows a useless address instead.";
  }

  @Override
  public String fix() {
    return "Use Arrays.toString(array) (or Arrays.deepToString for nested arrays).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitBinary(com.sun.source.tree.BinaryTree node, RuleContext ctx) {
        // string + array  (or array + string)
        if (node.getKind() == Tree.Kind.PLUS
            && (isString(node.getLeftOperand(), ctx) || isString(node.getRightOperand(), ctx))) {
          ExpressionTree arraySide = isArray(node.getLeftOperand(), ctx) ? node.getLeftOperand()
              : isArray(node.getRightOperand(), ctx) ? node.getRightOperand() : null;
          if (arraySide != null && !isCharArray(arraySide, ctx)) {
            ctx.report(arraySide, "Array in string concatenation prints its address, not "
                + "contents. Use Arrays.toString(...).");
          }
        }
        return super.visitBinary(node, ctx);
      }

      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 1
            && node.getMethodSelect() instanceof MemberSelectTree select
            && PRINT_METHODS.contains(select.getIdentifier().toString())
            && isArray(node.getArguments().get(0), ctx)
            && !isCharArray(node.getArguments().get(0), ctx)) {
          ctx.report(node, select.getIdentifier() + "(array) prints its address, not "
              + "contents. Use Arrays.toString(...).");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isString(ExpressionTree expr, RuleContext ctx) {
        return "java.lang.String".equals(ctx.qualifiedNameOf(type(expr, ctx)));
      }

      private boolean isArray(ExpressionTree expr, RuleContext ctx) {
        TypeMirror type = type(expr, ctx);
        return type != null && type.getKind() == TypeKind.ARRAY;
      }

      private boolean isCharArray(ExpressionTree expr, RuleContext ctx) {
        TypeMirror type = type(expr, ctx);
        // char[] is legitimately printable (String.valueOf(char[]), print(char[])).
        return type instanceof javax.lang.model.type.ArrayType arr
            && arr.getComponentType().getKind() == TypeKind.CHAR;
      }

      private @org.jspecify.annotations.Nullable TypeMirror type(ExpressionTree expr,
          RuleContext ctx) {
        return ctx.typeOf(new TreePath(getCurrentPath(), expr));
      }
    };
  }
}
