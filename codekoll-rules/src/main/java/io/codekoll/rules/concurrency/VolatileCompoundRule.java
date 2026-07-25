package io.codekoll.rules.concurrency;

import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

/**
 * CK-VOLATILE-COMPOUND: {@code ++}/{@code --}/{@code +=} on a volatile field —
 * read-modify-write is not atomic and volatile does not make it so; concurrent updates are
 * silently lost.
 */
public final class VolatileCompoundRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-VOLATILE-COMPOUND");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Compound update (++/--/+=) on a volatile field is not atomic";
  }

  @Override
  public String explanation() {
    return "volatile guarantees visibility, not atomicity. count++ is three operations "
        + "(read, add, write); two threads interleaving them both read the same value and "
        + "one increment is silently lost. Counters drift low under load — the discrepancy "
        + "only shows up in aggregate metrics, never as an exception.";
  }

  @Override
  public String fix() {
    return "Use AtomicInteger/AtomicLong (incrementAndGet), a LongAdder for hot counters, "
        + "or guard the update with a lock.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitUnary(UnaryTree node, RuleContext ctx) {
        Tree.Kind kind = node.getKind();
        if ((kind == Tree.Kind.PREFIX_INCREMENT || kind == Tree.Kind.POSTFIX_INCREMENT
            || kind == Tree.Kind.PREFIX_DECREMENT || kind == Tree.Kind.POSTFIX_DECREMENT)
            && isVolatileField(node.getExpression(), ctx)) {
          ctx.report(node, "++/-- on a volatile field is read-modify-write, not atomic — "
              + "concurrent increments are lost. Use AtomicInteger/AtomicLong.");
        }
        return super.visitUnary(node, ctx);
      }

      @Override
      public Void visitCompoundAssignment(CompoundAssignmentTree node, RuleContext ctx) {
        if (isVolatileField(node.getVariable(), ctx)) {
          ctx.report(node, "Compound assignment on a volatile field is read-modify-write, "
              + "not atomic — concurrent updates are lost. Use an Atomic* type or a lock.");
        }
        return super.visitCompoundAssignment(node, ctx);
      }

      private boolean isVolatileField(ExpressionTree target, RuleContext ctx) {
        Element symbol = ctx.trees().getElement(
            new TreePath(getCurrentPath(), NullFacts.unwrap(target)));
        return symbol != null
            && symbol.getKind() == ElementKind.FIELD
            && symbol.getModifiers().contains(Modifier.VOLATILE);
      }
    };
  }
}
