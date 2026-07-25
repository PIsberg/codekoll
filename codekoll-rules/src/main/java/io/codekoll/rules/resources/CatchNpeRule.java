package io.codekoll.rules.resources;

import com.sun.source.tree.CatchTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.type.TypeMirror;

/**
 * CK-CATCH-NPE: an explicit {@code catch (NullPointerException)} — using NPE for control
 * flow masks real bugs anywhere in the try body.
 */
public final class CatchNpeRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-CATCH-NPE");

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
    return "Explicit catch of NullPointerException";
  }

  @Override
  public String explanation() {
    return "Catching NPE turns 'this reference was unexpectedly null' — a bug — into "
        + "control flow, and it catches EVERY NPE in the try body, including ones from "
        + "completely unrelated code introduced later. The null bug the catch was written "
        + "for stays unfixed, and new null bugs get silently absorbed with it.";
  }

  @Override
  public String fix() {
    return "Null-check the specific access that can legitimately be null (or use "
        + "Optional); let unexpected NPEs crash loudly and get fixed.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitCatch(CatchTree node, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(
            new TreePath(getCurrentPath(), node.getParameter().getType()));
        if ("java.lang.NullPointerException".equals(ctx.qualifiedNameOf(type))) {
          ctx.report(node, "catch (NullPointerException) masks real bugs from the whole "
              + "try body. Null-check the specific access instead.");
        }
        return super.visitCatch(node, ctx);
      }
    };
  }
}
