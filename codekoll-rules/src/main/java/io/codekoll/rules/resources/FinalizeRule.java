package io.codekoll.rules.resources;

import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;

/**
 * CK-FINALIZE: overriding {@code Object.finalize()} — deprecated for removal,
 * non-deterministic, and a resurrection hazard.
 */
public final class FinalizeRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-FINALIZE");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.RESOURCES;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "finalize() override (deprecated for removal, unreliable cleanup)";
  }

  @Override
  public String explanation() {
    return "finalize() may run arbitrarily late, on any thread, or never — the JVM makes no "
        + "promises — and the mechanism is deprecated for removal, so the cleanup it "
        + "implements will silently stop existing on a future JDK. Resources 'cleaned up' "
        + "in finalize leak unpredictably today and deterministically tomorrow.";
  }

  @Override
  public String fix() {
    return "Implement AutoCloseable and close explicitly (try-with-resources), or register "
        + "a java.lang.ref.Cleaner for last-resort cleanup.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethod(MethodTree node, RuleContext ctx) {
        if (node.getName().contentEquals("finalize") && node.getParameters().isEmpty()
            && node.getBody() != null) {
          ctx.report(node, "finalize() is deprecated for removal and runs unpredictably "
              + "(or never). Use AutoCloseable + try-with-resources, or a Cleaner.");
        }
        return super.visitMethod(node, ctx);
      }
    };
  }
}
