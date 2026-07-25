package io.codekoll.rules.security;

import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import java.util.regex.Pattern;
import javax.lang.model.type.TypeMirror;

/**
 * CK-INSECURE-RANDOM: (a) {@code new Random(<constant seed>)} — a predictable sequence;
 * (b) {@code java.util.Random} used to initialize a variable whose name says it is
 * secret/token material.
 */
public final class InsecureRandomRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-INSECURE-RANDOM");

  private static final Pattern SECRET_NAME = Pattern.compile(
      "(?i).*(token|secret|session|salt|nonce|otp|apikey|api_key).*");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "java.util.Random with a constant seed, or used for secret material";
  }

  @Override
  public String explanation() {
    return "java.util.Random is a deterministic generator: a constant seed replays the "
        + "exact same sequence on every run, and even unseeded, its 48-bit state can be "
        + "recovered from a couple of outputs. Tokens, session ids and salts generated "
        + "from it are predictable to an attacker.";
  }

  @Override
  public String fix() {
    return "Use java.security.SecureRandom for anything security-relevant; keep "
        + "java.util.Random for simulations and tests.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitNewClass(NewClassTree node, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), node));
        if ("java.util.Random".equals(ctx.qualifiedNameOf(type))
            && node.getArguments().size() == 1
            && NullFacts.unwrap(node.getArguments().get(0))
                instanceof com.sun.source.tree.LiteralTree) {
          ctx.report(node, "new Random(<constant>) replays the same sequence every run — "
              + "predictable output. Seed from entropy, or use SecureRandom.");
        }
        return super.visitNewClass(node, ctx);
      }

      @Override
      public Void visitVariable(VariableTree node, RuleContext ctx) {
        if (node.getInitializer() != null
            && SECRET_NAME.matcher(node.getName().toString()).matches()) {
          TypeMirror initType =
              ctx.typeOf(new TreePath(getCurrentPath(), node.getInitializer()));
          String source = node.getInitializer().toString();
          boolean fromUtilRandom = source.contains("Math.random")
              || "java.util.Random".equals(ctx.qualifiedNameOf(initType))
              || source.matches(".*\\bnew Random\\(.*")
              || source.contains("ThreadLocalRandom");
          if (fromUtilRandom) {
            ctx.report(node, "'" + node.getName() + "' looks like secret material generated "
                + "from a predictable RNG. Use SecureRandom.");
          }
        }
        return super.visitVariable(node, ctx);
      }
    };
  }
}
