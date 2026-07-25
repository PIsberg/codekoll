package io.codekoll.rules.security;

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
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * CK-WEAK-TLS: {@code SSLContext.getInstance} with a protocol literal that has known breaks
 * (SSL*, TLSv1, TLSv1.1).
 */
public final class WeakTlsRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-WEAK-TLS");

  private static final Set<String> BROKEN_PROTOCOLS =
      Set.of("SSL", "SSLV2", "SSLV3", "TLSV1", "TLSV1.1");

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
    return "SSLContext requested with a broken TLS/SSL protocol version";
  }

  @Override
  public String explanation() {
    return "SSLv2/v3 and TLS 1.0/1.1 have practical attacks (POODLE, BEAST) and are "
        + "deprecated by RFC 8996. Connections negotiated at these versions can be "
        + "downgraded and decrypted — the padlock is there, the protection is not.";
  }

  @Override
  public String fix() {
    return "Request \"TLSv1.3\" (or \"TLSv1.2\" as the minimum) — and prefer "
        + "SSLContext.getDefault() so JVM security policy governs.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("getInstance")
            && !node.getArguments().isEmpty()
            && isSslContext(select, ctx)
            && node.getArguments().get(0) instanceof com.sun.source.tree.LiteralTree literal
            && literal.getValue() instanceof String protocol
            && BROKEN_PROTOCOLS.contains(protocol.toUpperCase(java.util.Locale.ROOT))) {
          ctx.report(node, "\"" + protocol + "\" has known practical attacks (RFC 8996 "
              + "deprecates it). Use \"TLSv1.3\" or \"TLSv1.2\".");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isSslContext(MemberSelectTree select, RuleContext ctx) {
        Element element = ctx.trees().getElement(
            new TreePath(getCurrentPath(), select.getExpression()));
        return element instanceof TypeElement type
            && "javax.net.ssl.SSLContext".equals(type.getQualifiedName().toString());
      }
    };
  }
}
