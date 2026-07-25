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
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * CK-CHRONO-UNSUPPORTED: {@code Instant.plus(n, ChronoUnit.MONTHS)} and friends — compiles,
 * always throws {@code UnsupportedTemporalTypeException}.
 */
public final class ChronoUnsupportedRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-CHRONO-UNSUPPORTED");

  /** Units estimated (not exact) — unsupported by Instant.plus/minus. */
  private static final Set<String> CALENDAR_UNITS =
      Set.of("WEEKS", "MONTHS", "YEARS", "DECADES", "CENTURIES", "MILLENNIA", "ERAS");
  /** Time-of-day units — unsupported by LocalDate.plus/minus. */
  private static final Set<String> TIME_UNITS =
      Set.of("NANOS", "MICROS", "MILLIS", "SECONDS", "MINUTES", "HOURS", "HALF_DAYS");

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
    return "Instant.plus(MONTHS) / LocalDate.plus(HOURS): always throws";
  }

  @Override
  public String explanation() {
    return "Instant has no calendar, LocalDate has no clock: Instant.plus(1, MONTHS) and "
        + "LocalDate.plus(2, HOURS) compile cleanly and throw "
        + "UnsupportedTemporalTypeException on EVERY execution. The java.time types are "
        + "precise about what they model — the compiler just cannot see it.";
  }

  @Override
  public String fix() {
    return "Convert first: instant.atZone(zone).plusMonths(1).toInstant(); "
        + "date.atStartOfDay().plusHours(2) — or use the unit-specific methods.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 2
            && node.getMethodSelect() instanceof MemberSelectTree select
            && (select.getIdentifier().contentEquals("plus")
                || select.getIdentifier().contentEquals("minus"))) {
          String unit = chronoUnitName(node.getArguments().get(1));
          if (unit != null) {
            TypeMirror receiver =
                ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
            String receiverName = ctx.qualifiedNameOf(receiver);
            if ("java.time.Instant".equals(receiverName) && CALENDAR_UNITS.contains(unit)) {
              ctx.report(node, "Instant has no calendar: plus/minus " + unit
                  + " ALWAYS throws UnsupportedTemporalTypeException. Convert via "
                  + "atZone(...) first.");
            } else if ("java.time.LocalDate".equals(receiverName)
                && TIME_UNITS.contains(unit)) {
              ctx.report(node, "LocalDate has no time-of-day: plus/minus " + unit
                  + " ALWAYS throws UnsupportedTemporalTypeException. Use "
                  + "atStartOfDay() first.");
            }
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private String chronoUnitName(com.sun.source.tree.ExpressionTree arg) {
        com.sun.source.tree.ExpressionTree unwrapped = NullFacts.unwrap(arg);
        if (unwrapped instanceof MemberSelectTree select
            && select.getExpression().toString().endsWith("ChronoUnit")) {
          return select.getIdentifier().toString();
        }
        return null;
      }
    };
  }
}
