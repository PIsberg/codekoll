package io.codekoll.rules.frameworks;

import com.sun.source.tree.ExpressionTree;
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
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * CK-SLF4J-PLACEHOLDER: SLF4J logging call whose constant format string's {@code {}}
 * placeholder count does not match the argument count (honoring the trailing-Throwable
 * convention).
 */
public final class Slf4jPlaceholderRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-SLF4J-PLACEHOLDER");

  private static final Set<String> LOG_METHODS =
      Set.of("trace", "debug", "info", "warn", "error");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.FRAMEWORKS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "SLF4J {} placeholder count does not match the arguments";
  }

  @Override
  public String explanation() {
    return "SLF4J substitutes {} placeholders positionally and SILENTLY ignores the "
        + "mismatch: extra arguments vanish from the log line, missing ones leave literal "
        + "'{}' in the output. The log statement that was supposed to capture the failing "
        + "order id prints '{}' instead — discovered exactly when the log is needed.";
  }

  @Override
  public String fix() {
    return "Match one {} per argument (a trailing Throwable is extra and gets the stack "
        + "trace). Count them after every edit to the message.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && LOG_METHODS.contains(select.getIdentifier().toString())
            && !node.getArguments().isEmpty()
            && isSlf4jLogger(select, ctx)
            && NullFacts.unwrap(node.getArguments().get(0)) instanceof LiteralTree literal
            && literal.getValue() instanceof String format) {
          int placeholders = countPlaceholders(format);
          int args = node.getArguments().size() - 1;
          boolean lastIsThrowable = args > 0
              && isThrowable(node.getArguments().get(node.getArguments().size() - 1), ctx);
          int effectiveArgs = lastIsThrowable && placeholders < args ? args - 1 : args;
          if (placeholders != effectiveArgs) {
            ctx.report(node, placeholders + " {} placeholder(s) but " + effectiveArgs
                + " argument(s)" + (lastIsThrowable ? " (+ trailing Throwable)" : "")
                + " — SLF4J silently drops the mismatch. Align them.");
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isSlf4jLogger(MemberSelectTree select, RuleContext ctx) {
        TypeMirror receiver =
            ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
        return "org.slf4j.Logger".equals(ctx.qualifiedNameOf(receiver));
      }

      private int countPlaceholders(String format) {
        int count = 0;
        int i = 0;
        while (i + 1 < format.length()) {
          if (format.charAt(i) == '{' && format.charAt(i + 1) == '}'
              && (i == 0 || format.charAt(i - 1) != '\\')) {
            count++;
            i += 2;
          } else {
            i++;
          }
        }
        return count;
      }

      private boolean isThrowable(ExpressionTree arg, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), arg));
        return ctx.isSubtypeOf(type, "java.lang.Throwable");
      }
    };
  }
}
