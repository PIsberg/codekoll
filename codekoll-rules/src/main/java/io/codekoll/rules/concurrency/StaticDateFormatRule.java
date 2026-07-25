package io.codekoll.rules.concurrency;

import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

/**
 * CK-STATIC-DATEFORMAT: a static (non-ThreadLocal) {@code SimpleDateFormat}/{@code Calendar}/
 * {@code NumberFormat} field — documented non-thread-safe; shared static instances corrupt
 * state under concurrent use.
 */
public final class StaticDateFormatRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-STATIC-DATEFORMAT");

  private static final Set<String> UNSAFE_TYPES = Set.of(
      "java.text.SimpleDateFormat", "java.text.DateFormat", "java.util.Calendar",
      "java.util.GregorianCalendar", "java.text.NumberFormat", "java.text.DecimalFormat");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.CONCURRENCY;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Static SimpleDateFormat/Calendar/NumberFormat shared across threads";
  }

  @Override
  public String explanation() {
    return "SimpleDateFormat, Calendar and NumberFormat keep mutable parse/format state and "
        + "are documented as not thread-safe. A static instance shared by concurrent "
        + "requests interleaves that state: dates parse to garbage values or throw "
        + "sporadic NumberFormatException — classic load-dependent corruption that never "
        + "reproduces locally.";
  }

  @Override
  public String fix() {
    return "Use java.time.format.DateTimeFormatter (immutable, thread-safe), or wrap the "
        + "legacy formatter in a ThreadLocal.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitVariable(VariableTree node, RuleContext ctx) {
        if (node.getModifiers().getFlags().contains(Modifier.STATIC)) {
          Element symbol = ctx.trees().getElement(getCurrentPath());
          if (symbol != null && symbol.getKind() == ElementKind.FIELD) {
            TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), node.getType()));
            if (UNSAFE_TYPES.contains(ctx.qualifiedNameOf(type))) {
              ctx.report(node, "Static " + node.getType() + " is shared by all threads and "
                  + "is not thread-safe — parse/format state corrupts under load. "
                  + "Use DateTimeFormatter or a ThreadLocal.");
            }
          }
        }
        return super.visitVariable(node, ctx);
      }
    };
  }
}
