package io.codekoll.rules.frameworks;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * CK-LOG-EXCEPTION-LOST: in a catch block, a logging call that includes the caught exception
 * only via string concatenation or getMessage() — the stack trace is lost.
 */
public final class LogExceptionLostRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-LOG-EXCEPTION-LOST");

  private static final Set<String> LOG_METHODS =
      Set.of("error", "warn", "info", "debug", "trace");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.FRAMEWORKS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Exception logged via string concat / getMessage() loses the stack trace";
  }

  @Override
  public String explanation() {
    return "log.error(\"failed: \" + e) or log.error(e.getMessage()) records only the "
        + "message text — the stack trace, the single most useful piece of a failure, is "
        + "gone. And getMessage() is frequently null (NPE, many wrapper exceptions), so the "
        + "log line can end up saying literally 'failed: null'.";
  }

  @Override
  public String fix() {
    return "Pass the exception as the last logger argument: log.error(\"failed for {}\", "
        + "id, e); — SLF4J then attaches the full stack trace.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitCatch(CatchTree node, RuleContext ctx) {
        String caught = node.getParameter().getName().toString();
        node.getBlock().accept(new TreeScanner<Void, Void>() {
          @Override
          public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
            checkLogCall(call, caught, ctx);
            return super.visitMethodInvocation(call, unused);
          }
        }, null);
        return super.visitCatch(node, ctx);
      }

      private void checkLogCall(MethodInvocationTree call, String caught, RuleContext ctx) {
        if (!(call.getMethodSelect() instanceof MemberSelectTree select)
            || !LOG_METHODS.contains(select.getIdentifier().toString())
            || !isLogger(select, ctx)) {
          return;
        }
        boolean mentionsInMessage = mentionsExceptionLossily(call, caught);
        boolean passesExceptionDirectly = call.getArguments().stream()
            .anyMatch(a -> isBareCaught(a, caught));
        if (mentionsInMessage && !passesExceptionDirectly) {
          ctx.report(call, "The exception is logged only via its message/concatenation — "
              + "the stack trace is lost. Pass '" + caught + "' as the last argument.");
        }
      }

      /** True when the caught var appears inside a concatenation or as e.getMessage(). */
      private boolean mentionsExceptionLossily(MethodInvocationTree call, String caught) {
        for (ExpressionTree arg : call.getArguments()) {
          Boolean lossy = arg.accept(new TreeScanner<Boolean, Void>() {
            @Override
            public Boolean visitBinary(BinaryTree binary, Void unused) {
              if (binary.getKind() == Tree.Kind.PLUS
                  && (refersTo(binary.getLeftOperand(), caught)
                      || refersTo(binary.getRightOperand(), caught))) {
                return Boolean.TRUE;
              }
              return super.visitBinary(binary, unused);
            }

            @Override
            public Boolean visitMethodInvocation(MethodInvocationTree inner, Void unused) {
              if (inner.getMethodSelect() instanceof MemberSelectTree sel
                  && sel.getIdentifier().contentEquals("getMessage")
                  && refersTo(sel.getExpression(), caught)) {
                return Boolean.TRUE;
              }
              return super.visitMethodInvocation(inner, unused);
            }

            @Override
            public Boolean reduce(@Nullable Boolean a, @Nullable Boolean b) {
              return Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b);
            }
          }, null);
          if (Boolean.TRUE.equals(lossy)) {
            return true;
          }
        }
        return false;
      }

      private boolean refersTo(ExpressionTree expr, String name) {
        return expr instanceof com.sun.source.tree.IdentifierTree id
            && id.getName().contentEquals(name);
      }

      private boolean isBareCaught(ExpressionTree arg, String caught) {
        return refersTo(arg, caught);
      }

      private boolean isLogger(MemberSelectTree select, RuleContext ctx) {
        TypeMirror receiver =
            ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
        String name = ctx.qualifiedNameOf(receiver);
        return "org.slf4j.Logger".equals(name)
            || name.endsWith(".Logger") || name.endsWith(".Log");
      }
    };
  }
}
