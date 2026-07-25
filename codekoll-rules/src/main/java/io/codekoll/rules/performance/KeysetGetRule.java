package io.codekoll.rules.performance;

import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import org.jspecify.annotations.Nullable;

/**
 * CK-KEYSET-GET: {@code for (K k : map.keySet())} whose body calls {@code map.get(k)} —
 * one wasted hash lookup per entry; {@code entrySet()} delivers both at once.
 */
public final class KeysetGetRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-KEYSET-GET");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.PERFORMANCE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Iterating keySet() then calling get(key) — use entrySet()";
  }

  @Override
  public String explanation() {
    return "The loop already visits every entry, then pays a full hash lookup (hash, probe, "
        + "equals) to fetch the value it was just standing on. entrySet() hands over key "
        + "and value together — same iteration, zero extra lookups.";
  }

  @Override
  public String fix() {
    return "for (Map.Entry<K, V> e : map.entrySet()) { ... e.getKey() ... e.getValue() ... } "
        + "— or map.forEach((k, v) -> ...).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitEnhancedForLoop(EnhancedForLoopTree node, RuleContext ctx) {
        // Shape: for (K key : <receiver>.keySet()) { ... <receiver>.get(key) ... }
        if (NullFacts.unwrap(node.getExpression()) instanceof MethodInvocationTree iterable
            && iterable.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("keySet")) {
          String mapExpr = select.getExpression().toString();
          String loopVar = node.getVariable().getName().toString();
          MethodInvocationTree get = findGet(node, mapExpr, loopVar);
          if (get != null) {
            ctx.report(get, "The value is one entrySet() away — get(" + loopVar + ") pays "
                + "a full hash lookup per iteration. Iterate map.entrySet() instead.");
          }
        }
        return super.visitEnhancedForLoop(node, ctx);
      }

      private @Nullable MethodInvocationTree findGet(EnhancedForLoopTree loop, String mapExpr,
          String loopVar) {
        return loop.getStatement().accept(new TreeScanner<MethodInvocationTree, Void>() {
          @Override
          public MethodInvocationTree visitMethodInvocation(MethodInvocationTree call,
              Void unused) {
            if (call.getMethodSelect() instanceof MemberSelectTree select
                && select.getIdentifier().contentEquals("get")
                && select.getExpression().toString().equals(mapExpr)
                && call.getArguments().size() == 1
                && call.getArguments().get(0).toString().equals(loopVar)) {
              return call;
            }
            return super.visitMethodInvocation(call, unused);
          }

          @Override
          public MethodInvocationTree reduce(@Nullable MethodInvocationTree a,
              @Nullable MethodInvocationTree b) {
            return a != null ? a : b;
          }
        }, null);
      }
    };
  }
}
