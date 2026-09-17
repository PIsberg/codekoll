package io.codekoll.rules.pqc;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import io.codekoll.rules.support.SourceKinds;
import java.util.EnumSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * CK-PQC-KEY-EXCHANGE: key establishment a quantum computer can break (RSA encryption, (EC)DH, XDH,
 * DHKEM, HPKE, classical TLS groups and suites). Warning, not error: the migration needs peers.
 */
public final class PqcKeyExchangeRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-PQC-KEY-EXCHANGE");

  private static final Set<RequestType> REPORTED = EnumSet.of(RequestType.KEY_AGREEMENT,
      RequestType.KEM, RequestType.CIPHER, RequestType.TLS_NAMED_GROUP, RequestType.TLS_CIPHER_SUITE);

  /** Classical agreement next to ML-KEM in one method is the recommended hybrid, not a finding. */
  private static final Set<RequestType> HYBRID_CANDIDATES =
      EnumSet.of(RequestType.KEY_AGREEMENT, RequestType.KEM);

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.PQC;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Quantum-vulnerable key establishment (RSA encryption, (EC)DH, XDH, DHKEM, HPKE, classical "
        + "TLS groups and suites)";
  }

  @Override
  public String explanation() {
    return "RSA encryption, Diffie-Hellman, ECDH, XDH, DHKEM and HPKE rest on problems Shor's algorithm "
        + "solves on a large quantum computer. Key establishment is the urgent case: traffic recorded "
        + "today can be decrypted once such a computer exists (harvest now, decrypt later). ML-KEM is "
        + "not a drop-in: it encapsulates a shared secret instead of encrypting or agreeing on one, "
        + "Cipher.getInstance(\"ML-KEM\") does not exist, and every peer must support it. Codekoll "
        + "reports the site and never rewrites code.";
  }

  @Override
  public String fix() {
    return "Move key establishment to ML-KEM (FIPS 203, ML-KEM-768 by default) and run it hybrid with "
        + "the classical algorithm until every peer migrates. ML-KEM is built into Java 24 and later "
        + "(KEM.getInstance(\"ML-KEM\")); on older releases use Bouncy Castle PQC.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      /** Resolved on the first finding only: most units have none and must not load platform symbols. */
      private @Nullable Boolean builtIn;

      @Override
      public Void visitCompilationUnit(CompilationUnitTree node, RuleContext ctx) {
        if (SourceKinds.isTestSource(ctx)) {
          return null;
        }
        builtIn = null;
        return super.visitCompilationUnit(node, ctx);
      }

      private boolean builtIn(RuleContext ctx) {
        Boolean known = builtIn;
        if (known == null) {
          known = PqcGuidance.builtInPqc(ctx.elements());
          builtIn = known;
        }
        return known;
      }

      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        PqcSites.match(getCurrentPath(), ctx)
            .filter(request -> REPORTED.contains(request.type()))
            .filter(request -> !request.vulnerable().isEmpty())
            .filter(request -> !HYBRID_CANDIDATES.contains(request.type())
                || !PqcSites.enclosingMethodContains(getCurrentPath(), ctx,
                    other -> other.selectsPostQuantum(Family.ML_KEM)))
            .ifPresent(request -> ctx.report(node,
                PqcGuidance.keyEstablishment(request.vulnerableNames(), builtIn(ctx))));
        return super.visitMethodInvocation(node, ctx);
      }
    };
  }
}
