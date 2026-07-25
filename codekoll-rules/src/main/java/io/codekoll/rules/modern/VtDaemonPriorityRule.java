package io.codekoll.rules.modern;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;

/**
 * CK-VT-DAEMON-PRIORITY: {@code setDaemon(false)} on a virtual thread throws
 * IllegalArgumentException; {@code setPriority} on one is silently ignored. Fires when the
 * receiver chain visibly originates from {@code Thread.ofVirtual()}/{@code startVirtualThread}.
 */
public final class VtDaemonPriorityRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-VT-DAEMON-PRIORITY");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.MODERN;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "setDaemon(false)/setPriority on a virtual thread";
  }

  @Override
  public String explanation() {
    return "Virtual threads are ALWAYS daemons: setDaemon(false) throws "
        + "IllegalArgumentException at runtime, every time. setPriority does not throw — "
        + "it is silently ignored, so the 'high-priority' virtual thread runs exactly like "
        + "the rest and the tuning is imaginary.";
  }

  @Override
  public String fix() {
    return "Drop both calls for virtual threads. Keep the JVM alive with a non-daemon "
        + "platform thread or explicit lifecycle management instead of setDaemon(false).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && isVisiblyVirtual(select.getExpression())) {
          String method = select.getIdentifier().toString();
          if ("setDaemon".equals(method) && isFalseLiteral(node)) {
            ctx.report(node, "Virtual threads are always daemons — setDaemon(false) throws "
                + "IllegalArgumentException every time.");
          } else if ("setPriority".equals(method)) {
            ctx.report(node, "setPriority on a virtual thread is silently ignored — the "
                + "tuning has no effect.");
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }

      /** The receiver expression itself contains the virtual-thread origin (direct chains). */
      private boolean isVisiblyVirtual(ExpressionTree receiver) {
        String text = receiver.toString();
        return text.contains("ofVirtual") || text.contains("startVirtualThread");
      }

      private boolean isFalseLiteral(MethodInvocationTree node) {
        return node.getArguments().size() == 1
            && NullFacts.unwrap(node.getArguments().get(0)) instanceof LiteralTree literal
            && Boolean.FALSE.equals(literal.getValue());
      }
    };
  }
}
