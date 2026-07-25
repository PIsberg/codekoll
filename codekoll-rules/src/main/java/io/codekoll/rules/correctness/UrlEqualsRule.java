package io.codekoll.rules.correctness;

import com.sun.source.tree.ExpressionTree;
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
 * CK-URL-EQUALS: {@code equals}/{@code hashCode} on {@code java.net.URL} — these perform
 * blocking DNS resolution and treat different hostnames resolving to the same IP as equal.
 */
public final class UrlEqualsRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-URL-EQUALS");

  private static final Set<String> METHODS = Set.of("equals", "hashCode");

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
    return "equals/hashCode on java.net.URL (blocking DNS, surprising equality)";
  }

  @Override
  public String explanation() {
    return "URL.equals and hashCode resolve the host to an IP over the network — a blocking "
        + "DNS lookup inside what looks like a pure comparison. Two different hostnames on "
        + "the same server compare equal, comparisons hang when DNS is slow, and a URL used "
        + "as a HashMap key performs network I/O on every put.";
  }

  @Override
  public String fix() {
    return "Use java.net.URI, which compares purely by its string components with no "
        + "network access.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && METHODS.contains(select.getIdentifier().toString())
            && isUrl(select.getExpression(), ctx)) {
          ctx.report(node, "URL." + select.getIdentifier() + " does a blocking DNS lookup "
              + "and has surprising equality semantics. Use java.net.URI.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isUrl(ExpressionTree receiver, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), receiver));
        return "java.net.URL".equals(ctx.qualifiedNameOf(type));
      }
    };
  }
}
