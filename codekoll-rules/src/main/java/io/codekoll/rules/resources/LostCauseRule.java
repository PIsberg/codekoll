package io.codekoll.rules.resources;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import org.jspecify.annotations.Nullable;

/**
 * CK-LOST-CAUSE: a catch block throwing a NEW exception that does not carry the caught one
 * as its cause — the original stack trace is lost forever. Any other reference to the caught
 * variable (logging, message extraction) exempts.
 */
public final class LostCauseRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-LOST-CAUSE");

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
    return "Rethrow drops the original exception (no cause)";
  }

  @Override
  public String explanation() {
    return "The catch block wraps the failure in a new exception but never passes the "
        + "caught one along: the original stack trace — the WHERE and WHY of the actual "
        + "failure — is gone forever. Production shows only the wrapper, pointing at the "
        + "catch block instead of the root cause.";
  }

  @Override
  public String fix() {
    return "Pass the caught exception as the cause: "
        + "throw new ServiceException(\"context\", e);";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitCatch(CatchTree node, RuleContext ctx) {
        String caught = node.getParameter().getName().toString();
        if (!referencesVariable(node, caught)) {
          ThrowTree wrappingThrow = findThrowOfNew(node);
          if (wrappingThrow != null) {
            ctx.report(wrappingThrow, "The new exception does not carry '" + caught
                + "' as its cause — the original stack trace is lost. Pass it: "
                + "new Exception(msg, " + caught + ").");
          }
        }
        return super.visitCatch(node, ctx);
      }

      private boolean referencesVariable(CatchTree node, String name) {
        Boolean found = node.getBlock().accept(new TreeScanner<Boolean, Void>() {
          @Override
          public Boolean visitIdentifier(IdentifierTree id, Void unused) {
            return id.getName().contentEquals(name) ? Boolean.TRUE : Boolean.FALSE;
          }

          @Override
          public Boolean reduce(@Nullable Boolean a, @Nullable Boolean b) {
            return Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b);
          }
        }, null);
        return Boolean.TRUE.equals(found);
      }

      private @Nullable ThrowTree findThrowOfNew(CatchTree node) {
        return node.getBlock().accept(new TreeScanner<ThrowTree, Void>() {
          @Override
          public ThrowTree visitThrow(ThrowTree throwTree, Void unused) {
            return throwTree.getExpression() instanceof NewClassTree ? throwTree : null;
          }

          @Override
          public ThrowTree reduce(@Nullable ThrowTree a, @Nullable ThrowTree b) {
            return a != null ? a : b;
          }
        }, null);
      }
    };
  }
}
