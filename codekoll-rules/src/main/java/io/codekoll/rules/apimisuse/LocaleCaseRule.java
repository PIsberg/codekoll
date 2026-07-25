package io.codekoll.rules.apimisuse;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * CK-LOCALE-CASE: {@code toUpperCase()}/{@code toLowerCase()} with no Locale — the default
 * locale makes protocol/identifier case-folding wrong in Turkish (dotless i).
 */
public final class LocaleCaseRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-LOCALE-CASE");

  private static final Set<String> CASE_METHODS = Set.of("toUpperCase", "toLowerCase");

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
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "toUpperCase/toLowerCase without an explicit Locale";
  }

  @Override
  public String explanation() {
    return "No-arg case conversion uses the JVM's default locale. In a Turkish locale "
        + "\"TITLE\".toLowerCase() is \"tıtle\" (dotless i), so \"title\".equals(...) fails "
        + "and protocol tokens, header names and file extensions mismatch — only for users "
        + "in certain regions, which is why it survives testing.";
  }

  @Override
  public String fix() {
    return "Pass Locale.ROOT for machine-facing text: s.toLowerCase(Locale.ROOT) — or use "
        + "equalsIgnoreCase for comparisons.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().isEmpty()
            && node.getMethodSelect() instanceof MemberSelectTree select
            && CASE_METHODS.contains(select.getIdentifier().toString())
            && isString(select.getExpression(), ctx)) {
          ctx.report(node, select.getIdentifier() + "() uses the default locale — wrong in "
              + "Turkish (dotless i). Pass Locale.ROOT for machine-facing text.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isString(com.sun.source.tree.ExpressionTree receiver, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), receiver));
        return "java.lang.String".equals(ctx.qualifiedNameOf(type));
      }
    };
  }
}
