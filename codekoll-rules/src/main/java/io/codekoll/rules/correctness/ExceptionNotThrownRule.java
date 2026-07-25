package io.codekoll.rules.correctness;

import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.type.TypeMirror;

/**
 * CK-EXCEPTION-NOT-THROWN: an exception is constructed as a bare statement — the
 * {@code throw} keyword was forgotten, so the error path silently does nothing.
 */
public final class ExceptionNotThrownRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-EXCEPTION-NOT-THROWN");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.CORRECTNESS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Exception constructed but never thrown";
  }

  @Override
  public String explanation() {
    return "The statement builds an exception object and immediately discards it — the "
        + "'throw' keyword is missing. The validation or error path it was guarding "
        + "silently falls through and execution continues as if everything were fine.";
  }

  @Override
  public String fix() {
    return "Add the throw keyword: throw new IllegalArgumentException(...);";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitExpressionStatement(ExpressionStatementTree node, RuleContext ctx) {
        if (node.getExpression() instanceof NewClassTree creation) {
          TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), creation));
          if (ctx.isSubtypeOf(type, "java.lang.Throwable")) {
            ctx.report(node, "The exception is created and discarded — 'throw' is missing: "
                + "throw " + creation + ";");
          }
        }
        return super.visitExpressionStatement(node, ctx);
      }
    };
  }
}
