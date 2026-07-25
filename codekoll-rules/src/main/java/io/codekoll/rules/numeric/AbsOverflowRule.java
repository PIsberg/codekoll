package io.codekoll.rules.numeric;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;

/**
 * CK-ABS-OVERFLOW: {@code Math.abs} of {@code hashCode()}/{@code Random.nextInt()} —
 * {@code Math.abs(Integer.MIN_VALUE)} is still negative, so abs-then-modulo indexing
 * intermittently produces a negative index.
 */
public final class AbsOverflowRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-ABS-OVERFLOW");

  private static final Set<String> RISKY_SOURCES = Set.of("hashCode", "nextInt", "nextLong");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.NUMERIC;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Math.abs of hashCode()/nextInt() can still be negative";
  }

  @Override
  public String explanation() {
    return "Integer.MIN_VALUE has no positive counterpart in 32 bits, so "
        + "Math.abs(Integer.MIN_VALUE) returns Integer.MIN_VALUE — still negative. "
        + "Math.abs(key.hashCode()) % buckets therefore intermittently yields a negative "
        + "index: an ArrayIndexOutOfBoundsException that strikes roughly once per four "
        + "billion hashes, unreproducible in tests.";
  }

  @Override
  public String fix() {
    return "Use Math.floorMod(key.hashCode(), buckets) — always non-negative — or "
        + "Random.nextInt(bound).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 1
            && node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("abs")
            && select.getExpression().toString().endsWith("Math")
            && isRiskySource(node.getArguments().get(0))) {
          ctx.report(node, "Math.abs(Integer.MIN_VALUE) is still negative — abs of a "
              + "hash/random int intermittently stays negative. "
              + "Use Math.floorMod(x, n) for indexing.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isRiskySource(ExpressionTree argument) {
        return NullFacts.unwrap(argument) instanceof MethodInvocationTree call
            && call.getMethodSelect() instanceof MemberSelectTree select
            && RISKY_SOURCES.contains(select.getIdentifier().toString());
      }
    };
  }
}
