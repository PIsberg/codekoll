package io.codekoll.rules.apimisuse;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.jspecify.annotations.Nullable;

/**
 * CK-SYSPROP-PARSE: {@code Boolean.getBoolean} / {@code Integer.getInteger} /
 * {@code Long.getLong} used as if they parsed their argument. They read the <em>system
 * property</em> named by it, so a value handed to them silently yields false or null.
 */
public final class SyspropParseRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-SYSPROP-PARSE");

  /** Lookup method → the wrapper declaring it. */
  private static final Map<String, String> LOOKUPS = Map.of(
      "getBoolean", "java.lang.Boolean",
      "getInteger", "java.lang.Integer",
      "getLong", "java.lang.Long");

  /**
   * Spellings a boolean VALUE is written in. Matched exactly rather than via case mapping:
   * locale-sensitive lowercasing is the wrong tool for ASCII keywords.
   */
  private static final Set<String> BOOLEAN_LITERALS =
      Set.of("true", "false", "TRUE", "FALSE", "True", "False");

  /** Lookup method → what the caller almost certainly meant. */
  private static final Map<String, String> INTENDED = Map.of(
      "getBoolean", "Boolean.parseBoolean",
      "getInteger", "Integer.parseInt",
      "getLong", "Long.parseLong");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.API_MISUSE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Boolean.getBoolean/Integer.getInteger/Long.getLong read a system property, "
        + "they do not parse their argument";
  }

  @Override
  public String explanation() {
    return "Despite the names, these three methods take a system-property NAME, not a value: "
        + "Boolean.getBoolean(s) is System.getProperty(s) compared to \"true\". Handing them a "
        + "value read from a config file, a request parameter or an environment variable "
        + "compiles cleanly and then looks up a property that was never set — the answer is "
        + "always false (or null for getInteger/getLong, which then NPEs on unboxing). The "
        + "feature flag stays off forever and no error is ever reported.";
  }

  @Override
  public String fix() {
    return "To turn a string into a value use Boolean.parseBoolean / Integer.parseInt / "
        + "Long.parseLong. Keep getBoolean/getInteger/getLong only for reading a system "
        + "property by its name.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        check(node, ctx);
        return super.visitMethodInvocation(node, ctx);
      }

      private void check(MethodInvocationTree node, RuleContext ctx) {
        if (!(node.getMethodSelect() instanceof MemberSelectTree select)
            || node.getArguments().isEmpty()) {
          return;
        }
        String method = select.getIdentifier().toString();
        String wrapper = LOOKUPS.get(method);
        if (wrapper == null || !isWrapperType(select, wrapper, ctx)) {
          return;
        }
        ExpressionTree arg = node.getArguments().get(0);
        String constant = constantString(arg, ctx);
        if (constant != null) {
          // A constant that reads as a VALUE is a provable mistake; anything else is a name.
          if (looksLikeValue(constant, method)) {
            report(node, method, "\"" + constant + "\" is a value, not a property name", ctx);
          }
          return;
        }
        // Non-constant: a computed property name would normally still carry a dotted literal.
        if (!mentionsDottedLiteral(arg, ctx)) {
          report(node, method, "the argument is a computed string, not a property name", ctx);
        }
      }

      private void report(Tree node, String method, String why, RuleContext ctx) {
        ctx.report(node, method + " looks up the system property named by its argument — "
            + why + ", so this always returns "
            + ("getBoolean".equals(method) ? "false" : "null") + ". Use "
            + INTENDED.get(method) + "(...) to parse a string instead.");
      }

      private boolean isWrapperType(MemberSelectTree select, String wrapper, RuleContext ctx) {
        Element element =
            ctx.trees().getElement(new TreePath(getCurrentPath(), select.getExpression()));
        return element instanceof TypeElement type
            && wrapper.equals(type.getQualifiedName().toString());
      }

      /** "true"/"false" for getBoolean, an integer literal for getInteger/getLong. */
      private boolean looksLikeValue(String text, String method) {
        String trimmed = text.strip();
        if ("getBoolean".equals(method)) {
          return BOOLEAN_LITERALS.contains(trimmed);
        }
        if (trimmed.isEmpty()) {
          return false;
        }
        String digits = trimmed.startsWith("-") || trimmed.startsWith("+")
            ? trimmed.substring(1) : trimmed;
        return !digits.isEmpty() && digits.chars().allMatch(Character::isDigit);
      }

      /** Compile-time String constant behind {@code expr}, or null when it has none. */
      private @Nullable String constantString(ExpressionTree expr, RuleContext ctx) {
        ExpressionTree e = unwrap(expr);
        if (e instanceof LiteralTree literal && literal.getValue() instanceof String s) {
          return s;
        }
        Element element = ctx.trees().getElement(new TreePath(getCurrentPath(), e));
        if (element instanceof VariableElement variable
            && variable.getConstantValue() instanceof String s) {
          return s;
        }
        return null;
      }

      /** True when any String constant inside {@code expr} is shaped like a property name. */
      private boolean mentionsDottedLiteral(ExpressionTree expr, RuleContext ctx) {
        ExpressionTree e = unwrap(expr);
        if (e instanceof BinaryTree binary && binary.getKind() == Tree.Kind.PLUS) {
          return mentionsDottedLiteral(binary.getLeftOperand(), ctx)
              || mentionsDottedLiteral(binary.getRightOperand(), ctx);
        }
        String constant = constantString(e, ctx);
        return constant != null && constant.indexOf('.') >= 0;
      }

      private ExpressionTree unwrap(ExpressionTree expr) {
        ExpressionTree e = expr;
        while (e instanceof ParenthesizedTree parens) {
          e = parens.getExpression();
        }
        return e;
      }
    };
  }
}
