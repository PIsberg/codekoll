package io.codekoll.rules.modern;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.type.TypeMirror;

/**
 * CK-DURATION-CALENDAR: {@code ZonedDateTime.plus(Duration.ofDays(n))} — Duration is exact
 * seconds; across a DST transition "+1 day" lands at the wrong wall-clock time.
 */
public final class DurationCalendarRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-DURATION-CALENDAR");

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
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "Duration.ofDays used for calendar arithmetic on zoned times";
  }

  @Override
  public String explanation() {
    return "Duration.ofDays(1) is exactly 86 400 seconds. Across a daylight-saving "
        + "transition a calendar day is 23 or 25 hours, so zonedTime.plus(Duration.ofDays"
        + "(1)) lands one hour off the same wall-clock time — the daily 09:00 job runs at "
        + "08:00 or 10:00 for half the year.";
  }

  @Override
  public String fix() {
    return "Use calendar units for calendar arithmetic: plusDays(1) or Period.ofDays(1); "
        + "keep Duration for exact elapsed time.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 1
            && node.getMethodSelect() instanceof MemberSelectTree select
            && (select.getIdentifier().contentEquals("plus")
                || select.getIdentifier().contentEquals("minus"))
            && isDurationOfDays(node.getArguments().get(0))
            && isZonedReceiver(select, ctx)) {
          ctx.report(node, "Duration.ofDays is exact seconds — across DST the result lands "
              + "an hour off the wall-clock time. Use plusDays / Period.ofDays.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isDurationOfDays(com.sun.source.tree.ExpressionTree arg) {
        return NullFacts.unwrap(arg) instanceof MethodInvocationTree call
            && call.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("ofDays")
            && select.getExpression().toString().endsWith("Duration");
      }

      private boolean isZonedReceiver(MemberSelectTree select, RuleContext ctx) {
        TypeMirror receiver =
            ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
        return "java.time.ZonedDateTime".equals(ctx.qualifiedNameOf(receiver));
      }
    };
  }
}
