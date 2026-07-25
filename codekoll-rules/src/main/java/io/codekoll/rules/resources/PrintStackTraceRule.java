package io.codekoll.rules.resources;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.type.TypeMirror;

/**
 * CK-PRINT-STACKTRACE: {@code e.printStackTrace()} writes to stderr, bypassing the logging
 * framework — invisible in aggregated production logs.
 */
public final class PrintStackTraceRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-PRINT-STACKTRACE");

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
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "printStackTrace() bypasses the logging framework";
  }

  @Override
  public String explanation() {
    return "printStackTrace() writes to raw stderr: no timestamps, no correlation ids, and "
        + "in most deployments not captured by the log aggregator at all. The failure "
        + "happened, was even 'logged' — and is nowhere to be found when debugging "
        + "production.";
  }

  @Override
  public String fix() {
    return "Log through the project's logger with the exception as the last argument: "
        + "log.error(\"operation failed\", e);";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().isEmpty()
            && node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("printStackTrace")) {
          TypeMirror receiver =
              ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
          if (ctx.isSubtypeOf(receiver, "java.lang.Throwable")) {
            ctx.report(node, "printStackTrace() goes to raw stderr — lost in production. "
                + "Use the logger: log.error(\"...\", e).");
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }
    };
  }
}
