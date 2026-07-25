package io.codekoll.rules.security;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import io.codekoll.rules.support.TaintShapes;
import javax.lang.model.type.TypeMirror;

/**
 * CK-EXEC-CONCAT: {@code Runtime.exec}/{@code ProcessBuilder} arguments built by
 * concatenating literals with non-constant expressions — the command-injection shape.
 */
public final class ExecConcatRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-EXEC-CONCAT");

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
    return "Shell command built by concatenating variables";
  }

  @Override
  public String explanation() {
    return "A variable concatenated into a command line becomes part of the COMMAND: a "
        + "filename of \"x; rm -rf /\" (or \"& del /q *\") runs the attacker's program "
        + "with the application's privileges. Command injection is remote code execution "
        + "wherever any part of the string is user-influenced.";
  }

  @Override
  public String fix() {
    return "Use the list form with each argument separate: new ProcessBuilder(\"tool\", "
        + "\"--input\", filename) — arguments are then never parsed as shell syntax.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (!node.getArguments().isEmpty()
            && node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("exec")
            && isRuntime(select, ctx)
            && TaintShapes.isLiteralPlusNonConstant(node.getArguments().get(0))) {
          ctx.report(node, "Variables concatenated into the command line become commands — "
              + "command injection. Use ProcessBuilder's list form.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      @Override
      public Void visitNewClass(NewClassTree node, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), node));
        if ("java.lang.ProcessBuilder".equals(ctx.qualifiedNameOf(type))
            && node.getArguments().stream()
                .anyMatch(TaintShapes::isLiteralPlusNonConstant)) {
          ctx.report(node, "A concatenated argument mixes command syntax with data — "
              + "command injection. Pass each argument as its own list element.");
        }
        return super.visitNewClass(node, ctx);
      }

      private boolean isRuntime(MemberSelectTree select, RuleContext ctx) {
        TypeMirror receiver =
            ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
        return "java.lang.Runtime".equals(ctx.qualifiedNameOf(receiver));
      }
    };
  }
}
