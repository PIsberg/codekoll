package io.codekoll.rules.correctness;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;

/**
 * CK-EQUALS-NULL-ARG: {@code x.equals(null)} — the equals contract guarantees {@code false}
 * (and a broken implementation throws NPE). The author almost always meant {@code x == null}.
 */
public final class EqualsNullArgRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-EQUALS-NULL-ARG");

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
    return "equals(null) always returns false by contract";
  }

  @Override
  public String explanation() {
    return "The equals() contract requires x.equals(null) to return false, always — so the "
        + "expression is a constant, and a null receiver additionally throws an NPE before "
        + "the check even runs. The null test the author intended never happens.";
  }

  @Override
  public String fix() {
    return "Use x == null for a null check (or Objects.isNull(x) in a method reference "
        + "position).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 1
            && node.getArguments().get(0).getKind() == Tree.Kind.NULL_LITERAL
            && node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("equals")) {
          ctx.report(node, "equals(null) is false by contract (or NPEs). "
              + "Use '" + select.getExpression() + " == null'.");
        }
        return super.visitMethodInvocation(node, ctx);
      }
    };
  }
}
