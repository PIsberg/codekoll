package io.codekoll.rules.security;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
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
import javax.lang.model.type.TypeMirror;

/**
 * CK-TRUST-ALL: an {@code X509TrustManager} whose check methods are empty, or a
 * {@code HostnameVerifier} whose verify is constantly true — TLS validation disabled.
 */
public final class TrustAllRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-TRUST-ALL");

  private static final Set<String> CHECK_METHODS =
      Set.of("checkClientTrusted", "checkServerTrusted");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.SECURITY;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Trust-all TrustManager / always-true HostnameVerifier";
  }

  @Override
  public String explanation() {
    return "An empty checkServerTrusted accepts EVERY certificate — self-signed, expired, "
        + "forged for any hostname. TLS still encrypts, but to whoever answered: a "
        + "man-in-the-middle presents any certificate and reads everything. These 'just "
        + "for testing' managers ship to production constantly.";
  }

  @Override
  public String fix() {
    return "Trust the real CA chain; for internal CAs, load the CA cert into a proper "
        + "TrustManagerFactory. Never bypass validation, not even 'temporarily'.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethod(MethodTree node, RuleContext ctx) {
        if (CHECK_METHODS.contains(node.getName().toString())
            && node.getBody() != null
            && node.getBody().getStatements().isEmpty()
            && insideTrustManager(ctx)) {
          ctx.report(node, "Empty " + node.getName() + " accepts every certificate — TLS "
              + "validation is disabled and MITM is trivial. Trust the real CA chain.");
        }
        if (node.getName().contentEquals("verify")
            && node.getBody() != null
            && isConstantTrue(node.getBody())
            && insideHostnameVerifier(ctx)) {
          ctx.report(node, "verify() returning constant true disables hostname "
              + "verification — any certificate matches any host. Validate properly.");
        }
        return super.visitMethod(node, ctx);
      }

      @Override
      public Void visitLambdaExpression(LambdaExpressionTree node, RuleContext ctx) {
        // (hostname, session) -> true assigned/passed where a HostnameVerifier goes.
        if (node.getParameters().size() == 2 && isLambdaConstantTrue(node)) {
          TypeMirror target = ctx.typeOf(getCurrentPath());
          if ("javax.net.ssl.HostnameVerifier".equals(ctx.qualifiedNameOf(target))) {
            ctx.report(node, "Always-true HostnameVerifier lambda disables hostname "
                + "verification — any certificate matches any host.");
          }
        }
        return super.visitLambdaExpression(node, ctx);
      }

      private boolean isLambdaConstantTrue(LambdaExpressionTree node) {
        Tree body = node.getBody();
        if (body instanceof com.sun.source.tree.ExpressionTree expr) {
          return isTrueLiteral(expr);
        }
        return body instanceof BlockTree block && isConstantTrue(block);
      }

      private boolean isConstantTrue(BlockTree block) {
        if (block.getStatements().size() != 1) {
          return false;
        }
        StatementTree only = block.getStatements().get(0);
        return only instanceof ReturnTree ret && ret.getExpression() != null
            && isTrueLiteral(ret.getExpression());
      }

      private boolean isTrueLiteral(com.sun.source.tree.ExpressionTree expr) {
        return NullFacts.unwrap(expr) instanceof LiteralTree literal
            && Boolean.TRUE.equals(literal.getValue());
      }

      private boolean insideTrustManager(RuleContext ctx) {
        return enclosingClassImplements("javax.net.ssl.X509TrustManager", ctx);
      }

      private boolean insideHostnameVerifier(RuleContext ctx) {
        return enclosingClassImplements("javax.net.ssl.HostnameVerifier", ctx);
      }

      private boolean enclosingClassImplements(String fqn, RuleContext ctx) {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          if (p.getLeaf() instanceof ClassTree) {
            TypeMirror type = ctx.typeOf(p);
            return ctx.isSubtypeOf(type, fqn);
          }
        }
        return false;
      }
    };
  }
}
