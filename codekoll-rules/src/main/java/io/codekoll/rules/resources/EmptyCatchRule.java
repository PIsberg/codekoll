package io.codekoll.rules.resources;

import com.sun.source.tree.CatchTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;

/**
 * CK-EMPTY-CATCH: a catch block with zero statements swallows the exception silently.
 * Exemption: exception variable named {@code ignored}/{@code ignore}/{@code expected}.
 */
public final class EmptyCatchRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-EMPTY-CATCH");
  private static final Set<String> EXEMPT_NAMES = Set.of("ignored", "ignore", "expected");

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
    return "Empty catch block silently swallows the exception";
  }

  @Override
  public String explanation() {
    return "The catch block contains no statements, so the caught exception disappears without "
        + "a trace. The program continues as if nothing happened, and the failure becomes "
        + "impossible to diagnose in production.";
  }

  @Override
  public String fix() {
    return "Log the exception, rethrow it (wrapped, with the original as cause), or rename the "
        + "exception variable to 'ignored' if dropping it is genuinely intentional.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitCatch(CatchTree node, RuleContext ctx) {
        if (node.getBlock().getStatements().isEmpty()
            && !EXEMPT_NAMES.contains(node.getParameter().getName().toString())) {
          ctx.report(node, "Empty catch block swallows the exception. Log it, rethrow it, or "
              + "rename the variable to 'ignored' if intentional.");
        }
        return super.visitCatch(node, ctx);
      }
    };
  }
}
