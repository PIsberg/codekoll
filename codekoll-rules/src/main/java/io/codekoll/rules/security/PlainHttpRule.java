package io.codekoll.rules.security;

import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Locale;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * CK-PLAIN-HTTP: an {@code http://} literal used to build a URL/URI/request — cleartext
 * transport. Local, test, documentation and XML-namespace hosts are exempt.
 */
public final class PlainHttpRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-PLAIN-HTTP");

  private static final Set<String> URL_TYPES = Set.of(
      "java.net.URL", "java.net.URI");

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
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "http:// URL built for network use (cleartext transport)";
  }

  @Override
  public String explanation() {
    return "Plain HTTP sends everything — credentials, tokens, payloads — readable and "
        + "modifiable by every network hop. Anyone on the path can capture the session or "
        + "inject content; on mobile/hotel/coffee-shop networks that is a practical, "
        + "everyday attack, not a theoretical one.";
  }

  @Override
  public String fix() {
    return "Use https:// (and consider HSTS on the server side). Keep http:// only for "
        + "localhost tooling and XML namespace identifiers.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitNewClass(NewClassTree node, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), node));
        if (URL_TYPES.contains(ctx.qualifiedNameOf(type))
            && !node.getArguments().isEmpty()
            && node.getArguments().get(0) instanceof LiteralTree literal
            && literal.getValue() instanceof String url
            && isRiskyPlainHttp(url)) {
          ctx.report(node, "http:// is cleartext — every network hop can read and modify "
              + "the traffic. Use https://.");
        }
        return super.visitNewClass(node, ctx);
      }

      // The loopback/any-host literals here are the rule's EXEMPTION data (local traffic
      // is not flagged), not endpoints this code connects to.
      @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
      private boolean isRiskyPlainHttp(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        if (!u.startsWith("http://")) {
          return false;
        }
        String host = u.substring("http://".length());
        return !(host.startsWith("localhost") || host.startsWith("127.0.0.1")
            || host.startsWith("[::1]") || host.startsWith("0.0.0.0")
            || host.contains(".local") || host.contains(".test")
            || host.startsWith("example.") || host.contains("example.com")
            || host.contains("www.w3.org") || host.contains("xmlns")
            || host.contains("schemas."));
      }
    };
  }
}
