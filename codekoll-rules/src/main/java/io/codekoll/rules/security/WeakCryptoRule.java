package io.codekoll.rules.security;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.jspecify.annotations.Nullable;

/**
 * CK-CRYPTO-WEAK: {@code getInstance("MD5")} and friends — broken or deprecated algorithms
 * requested by string literal (or constant). MD5/SHA-1/DES/RC4/… are flagged as errors;
 * DESede as info.
 */
public final class WeakCryptoRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-CRYPTO-WEAK");

  private static final Set<String> FACTORY_TYPES = Set.of(
      "java.security.MessageDigest",
      "javax.crypto.Cipher",
      "javax.crypto.Mac",
      "javax.crypto.KeyGenerator",
      "javax.crypto.SecretKeyFactory");

  private static final Map<String, String> BROKEN = Map.ofEntries(
      Map.entry("MD2", "MD2 is cryptographically broken."),
      Map.entry("MD5", "MD5 is cryptographically broken (collision attacks)."),
      Map.entry("SHA-1", "SHA-1 is cryptographically broken (SHAttered collision)."),
      Map.entry("SHA1", "SHA-1 is cryptographically broken (SHAttered collision)."),
      Map.entry("DES", "DES has a 56-bit key and is brute-forceable."),
      Map.entry("RC2", "RC2 is obsolete and considered weak."),
      Map.entry("RC4", "RC4 has known biases and is prohibited (RFC 7465)."),
      Map.entry("ARCFOUR", "RC4/ARCFOUR has known biases and is prohibited (RFC 7465)."),
      Map.entry("BLOWFISH", "Blowfish has a 64-bit block size (birthday-bound attacks)."));

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
    return "Weak or broken cryptographic algorithm requested via getInstance(...)";
  }

  @Override
  public String explanation() {
    return "The code requests a cryptographic algorithm that is broken (MD5, SHA-1, DES, RC4) "
        + "or weakened (DESede, ECB mode). It runs fine — and produces hashes or ciphertext "
        + "an attacker can forge or break.";
  }

  @Override
  public String fix() {
    return "Use SHA-256 or stronger for digests, AES/GCM/NoPadding for ciphers, and "
        + "HmacSHA256 for MACs.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("getInstance")
            && !node.getArguments().isEmpty()
            && isCryptoFactory(select.getExpression(), ctx)) {
          String value = constantString(node.getArguments().get(0), ctx);
          if (value != null) {
            check(node, value, ctx);
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isCryptoFactory(ExpressionTree receiver, RuleContext ctx) {
        Element element = ctx.trees().getElement(new TreePath(getCurrentPath(), receiver));
        return element instanceof TypeElement type
            && FACTORY_TYPES.contains(type.getQualifiedName().toString());
      }

      private @Nullable String constantString(ExpressionTree arg, RuleContext ctx) {
        if (arg instanceof LiteralTree literal && literal.getValue() instanceof String s) {
          return s;
        }
        if (arg instanceof IdentifierTree || arg instanceof MemberSelectTree) {
          Element element = ctx.trees().getElement(new TreePath(getCurrentPath(), arg));
          if (element instanceof VariableElement variable
              && variable.getConstantValue() instanceof String s) {
            return s;
          }
        }
        return null;
      }

      private void check(MethodInvocationTree node, String transformation, RuleContext ctx) {
        String[] parts = transformation.split("/");
        String algorithm = parts[0].trim().toUpperCase(Locale.ROOT);
        String broken = BROKEN.get(algorithm);
        if (broken != null) {
          ctx.report(node, broken + " Use SHA-256 or stronger.");
        } else if ("DESEDE".equals(algorithm)) {
          ctx.report(node, "DESede (3DES) is deprecated (64-bit blocks, sweet32). Prefer AES.");
        } else if (parts.length > 1 && "ECB".equalsIgnoreCase(parts[1].trim())) {
          ctx.report(node, "ECB mode leaks plaintext structure (identical blocks encrypt "
              + "identically). Use GCM or another authenticated mode.");
        }
      }
    };
  }
}
