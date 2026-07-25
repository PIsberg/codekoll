package io.codekoll.rules.correctness;

import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * CK-FORMAT-MISMATCH: {@code String.format}/{@code printf}/{@code formatted} with a constant
 * format string whose conversion count does not match the argument count.
 */
public final class FormatMismatchRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-FORMAT-MISMATCH");

  // %[argument_index$][flags][width][.precision]conversion — %% and %n take no argument.
  private static final Pattern SPECIFIER =
      Pattern.compile("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z%]");

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
    return "Format string conversions do not match the arguments";
  }

  @Override
  public String explanation() {
    return "String.format counts %s/%d/... at runtime: too few arguments throws "
        + "MissingFormatArgumentException, and a type mismatch (a String for %d) throws "
        + "IllegalFormatConversionException. Both crash exactly the log or error path they "
        + "were meant to describe — usually while something else is already going wrong.";
  }

  @Override
  public String fix() {
    return "Match one argument per conversion (%% and %n take none); check after every "
        + "edit to the format string.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        FormatCall call = classify(node, ctx);
        if (call != null) {
          int required = countConversions(call.format());
          if (required >= 0 && required != call.argCount()) {
            ctx.report(node, "Format string needs " + required + " argument(s) but "
                + call.argCount() + " provided. Match one per %conversion.");
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private @Nullable FormatCall classify(MethodInvocationTree node, RuleContext ctx) {
        if (!(node.getMethodSelect() instanceof MemberSelectTree select)) {
          return null;
        }
        String name = select.getIdentifier().toString();
        var args = node.getArguments();
        // String.format(fmt, args...) / printf(fmt, args...): format is arg 0.
        if (("format".equals(name) || "printf".equals(name))
            && isStringType(select.getExpression(), ctx)
            && !args.isEmpty()) {
          String fmt = constant(args.get(0));
          return fmt == null ? null : new FormatCall(fmt, args.size() - 1);
        }
        // "template".formatted(args...): format is the receiver.
        if ("formatted".equals(name)) {
          String fmt = constant(select.getExpression());
          return fmt == null ? null : new FormatCall(fmt, args.size());
        }
        return null;
      }

      private boolean isStringType(com.sun.source.tree.ExpressionTree expr, RuleContext ctx) {
        // String.format (static) — receiver is the String type name.
        return expr.toString().endsWith("String") || expr.toString().endsWith("out")
            || isPrintStream(expr, ctx);
      }

      private boolean isPrintStream(com.sun.source.tree.ExpressionTree expr, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), expr));
        return ctx.isSubtypeOf(type, "java.io.PrintStream");
      }

      private @Nullable String constant(com.sun.source.tree.ExpressionTree expr) {
        return NullFacts.unwrap(expr) instanceof LiteralTree literal
            && literal.getValue() instanceof String s ? s : null;
      }

      /** Conversions requiring an argument (excludes %% and %n). -1 if the string is odd. */
      private int countConversions(String format) {
        int count = 0;
        Matcher matcher = SPECIFIER.matcher(format);
        while (matcher.find()) {
          char conversion = matcher.group().charAt(matcher.group().length() - 1);
          if (conversion != '%' && conversion != 'n') {
            count++;
          }
        }
        return count;
      }
    };
  }

  private record FormatCall(String format, int argCount) {}
}
