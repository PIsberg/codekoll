package io.codekoll.rules.correctness;

import com.sun.source.tree.LiteralTree;
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
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * CK-WEEK-YEAR-FORMAT: date pattern using {@code YYYY} (week-based year) with {@code MM}/
 * {@code dd} — dates around New Year silently shift a year.
 */
public final class WeekYearFormatRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-WEEK-YEAR-FORMAT");

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
    return "YYYY (week-based year) used in a calendar date pattern";
  }

  @Override
  public String explanation() {
    return "Uppercase YYYY is the WEEK-BASED year (ISO week numbering), not the calendar "
        + "year. Combined with MM/dd it agrees with yyyy for ~360 days — then December 29-31 "
        + "formats as NEXT year (2024-12-30 becomes 2025-12-30). The classic silent "
        + "end-of-December production bug, invisible in any test not run that week.";
  }

  @Override
  public String fix() {
    return "Use lowercase yyyy (or uuuu) for calendar dates; YYYY only belongs together "
        + "with ww (week-of-year).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("ofPattern")
            && isDateTimeFormatter(select, ctx)) {
          checkPattern(node, firstStringLiteral(node.getArguments()), ctx);
        }
        return super.visitMethodInvocation(node, ctx);
      }

      @Override
      public Void visitNewClass(NewClassTree node, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), node));
        if ("java.text.SimpleDateFormat".equals(ctx.qualifiedNameOf(type))) {
          checkPattern(node, firstStringLiteral(node.getArguments()), ctx);
        }
        return super.visitNewClass(node, ctx);
      }

      private boolean isDateTimeFormatter(MemberSelectTree select, RuleContext ctx) {
        Element element = ctx.trees().getElement(
            new TreePath(getCurrentPath(), select.getExpression()));
        return element instanceof TypeElement type
            && "java.time.format.DateTimeFormatter".equals(
                type.getQualifiedName().toString());
      }

      private @Nullable String firstStringLiteral(
          java.util.List<? extends com.sun.source.tree.ExpressionTree> args) {
        return !args.isEmpty() && args.get(0) instanceof LiteralTree literal
            && literal.getValue() instanceof String s ? s : null;
      }

      private void checkPattern(com.sun.source.tree.Tree node, @Nullable String pattern,
          RuleContext ctx) {
        if (pattern != null && pattern.contains("YYYY")
            && (pattern.contains("MM") || pattern.contains("dd"))
            && !pattern.contains("ww")) {
          ctx.report(node, "YYYY is the WEEK-based year: Dec 29-31 formats as next year. "
              + "Use yyyy for calendar dates.");
        }
      }
    };
  }
}
