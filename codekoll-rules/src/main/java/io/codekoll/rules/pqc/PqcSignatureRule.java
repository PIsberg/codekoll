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

/**
 * CK-PQC-SIGNATURE: signatures a quantum computer can forge (RSA, RSASSA-PSS, DSA, ECDSA, EdDSA),
 * including TLS signature schemes and XML signature methods.
 */
public final class PqcSignatureRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-PQC-SIGNATURE");

  private static final Set<RequestType> REPORTED = EnumSet.of(RequestType.SIGNATURE,
      RequestType.TLS_SIGNATURE_SCHEME, RequestType.XML_SIGNATURE_METHOD);

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
    return "Quantum-vulnerable digital signature (RSA, RSASSA-PSS, DSA, ECDSA, EdDSA; TLS signature "
        + "schemes; XML signature methods)";
  }

  @Override
  public String explanation() {
    return "RSA, RSASSA-PSS, DSA, ECDSA and EdDSA signatures rest on problems Shor's algorithm solves on "
        + "a large quantum computer, which could then forge them. The deadline is later than for key "
        + "establishment, because a signature only has to resist forgery when it is verified, but "
        + "long-lived signatures and trust anchors are exposed first. ML-DSA is not a drop-in: keys and "
        + "signatures are several times larger and every verifier must support it. Codekoll reports the "
        + "site and never rewrites code.";
  }

  @Override
  public String fix() {
    return "Sign with ML-DSA (FIPS 204, ML-DSA-65 by default); while verifiers differ, sign with both "
        + "algorithms (hybrid). ML-DSA is built into Java 24 and later (Signature.getInstance(\"ML-DSA\")); "
        + "on older releases use Bouncy Castle PQC.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      private boolean builtIn;

      @Override
      public Void visitCompilationUnit(CompilationUnitTree node, RuleContext ctx) {
        if (SourceKinds.isTestSource(node)) {
          return null;
        }
        builtIn = PqcGuidance.builtInPqc(ctx.elements());
        return super.visitCompilationUnit(node, ctx);
      }

      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        PqcSites.match(getCurrentPath(), ctx)
            .filter(request -> REPORTED.contains(request.type()))
            .filter(request -> !request.vulnerable().isEmpty())
            .ifPresent(request -> ctx.report(node,
                PqcGuidance.signature(request.vulnerableNames(), builtIn)));
        return super.visitMethodInvocation(node, ctx);
      }
    };
  }
}
