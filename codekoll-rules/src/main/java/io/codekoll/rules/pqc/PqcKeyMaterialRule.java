package io.codekoll.rules.pqc;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
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
 * CK-PQC-KEY-MATERIAL: quantum-vulnerable keys and parameters (RSA, EC, DSA, DH, XDH, EdDSA). INFO,
 * because the key's use is decided elsewhere and those sites carry the warnings.
 */
public final class PqcKeyMaterialRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-PQC-KEY-MATERIAL");

  private static final Set<RequestType> FACTORIES = EnumSet.of(RequestType.KEY_PAIR_GENERATOR,
      RequestType.KEY_FACTORY, RequestType.ALGORITHM_PARAMETERS,
      RequestType.ALGORITHM_PARAMETER_GENERATOR);

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
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "Quantum-vulnerable key pair, key factory or algorithm parameters (RSA, EC, DSA, DH, XDH, "
        + "EdDSA)";
  }

  @Override
  public String explanation() {
    return "RSA, EC, DSA, Diffie-Hellman, XDH and EdDSA keys are quantum-vulnerable: Shor's algorithm "
        + "recovers the private key from the public one, so traffic recorded today under such keys can "
        + "be decrypted later and signatures made with them can be forged. Replacing them is not a "
        + "drop-in: ML-KEM and ML-DSA keys have different types and sizes and peers must support them. "
        + "Reported as info because the key's use is decided elsewhere; the key-establishment and "
        + "signature sites carry the warnings. Codekoll never rewrites code.";
  }

  @Override
  public String fix() {
    return "Generate ML-KEM keys for key establishment, hybrid with the classical key while peers "
        + "migrate, and ML-DSA keys for signatures. Both are built into Java 24 and later; on older "
        + "releases use Bouncy Castle PQC.";
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
        check(node, ctx);
        return super.visitMethodInvocation(node, ctx);
      }

      @Override
      public Void visitNewClass(NewClassTree node, RuleContext ctx) {
        check(node, ctx);
        return super.visitNewClass(node, ctx);
      }

      @Override
      public Void visitMemberSelect(MemberSelectTree node, RuleContext ctx) {
        check(node, ctx);
        return super.visitMemberSelect(node, ctx);
      }

      private void check(Tree node, RuleContext ctx) {
        TreePath path = getCurrentPath();
        PqcSites.match(path, ctx)
            .filter(request -> !request.vulnerable().isEmpty())
            .filter(request -> FACTORIES.contains(request.type())
                || request.type() == RequestType.PARAMETER_SPEC
                    && !PqcSites.enclosingMethodContains(path, ctx,
                        other -> FACTORIES.contains(other.type()) && !other.vulnerable().isEmpty()))
            .ifPresent(request -> ctx.report(node,
                PqcGuidance.keyMaterial(request.vulnerableNames(), request.role(), builtIn(ctx))));
      }
    };
  }
}
