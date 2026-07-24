package io.codekoll.rules.nullness;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.NullFacts.Fact;
import io.codekoll.rules.support.NullFacts.Kind;
import io.codekoll.rules.support.RuleContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CK-IMPOSSIBLE-COND: contradictory null conditions inside one boolean expression.
 *
 * <p>In an {@code &&} chain: once {@code x == null} holds, a later conjunct that
 * dereferences {@code x} or asserts {@code x != null} can never succeed — the branch is dead
 * (and would NPE if reached). In an {@code ||} chain the guard is inverted: after
 * {@code x != null} fails, a later disjunct dereferencing {@code x} is a guaranteed NPE.
 *
 * <p>Facts live on simple identifiers only and are conservatively dropped for a conjunct
 * containing method calls (the call's result may correlate with anything).
 */
public final class ImpossibleCondRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-IMPOSSIBLE-COND");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.NULLNESS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Null-check chain that contradicts itself (dead branch or guaranteed NPE)";
  }

  @Override
  public String explanation() {
    return "One side of the condition assumes a variable is null while another side "
        + "dereferences it or asserts it is non-null. Both can never hold at once: either "
        + "the branch is dead code, or evaluating it throws a NullPointerException — "
        + "typically a mistyped && / || or an inverted comparison.";
  }

  @Override
  public String fix() {
    return "Re-check the intended logic: usually 'x != null && x.length() > 5' "
        + "(guard, then use) or the || equivalent 'x == null || x.isEmpty()'.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitBinary(BinaryTree node, RuleContext ctx) {
        // Only analyze the TOP of a chain: skip nodes whose parent is the same operator.
        if ((node.getKind() == Tree.Kind.CONDITIONAL_AND
            || node.getKind() == Tree.Kind.CONDITIONAL_OR)
            && !parentHasSameKind(node)) {
          analyzeChain(node, ctx);
        }
        return super.visitBinary(node, ctx);
      }

      private boolean parentHasSameKind(BinaryTree node) {
        Tree parent = getCurrentPath().getParentPath().getLeaf();
        return parent.getKind() == node.getKind();
      }

      private void analyzeChain(BinaryTree chain, RuleContext ctx) {
        boolean isAnd = chain.getKind() == Tree.Kind.CONDITIONAL_AND;
        List<ExpressionTree> operands = NullFacts.flatten(chain, chain.getKind());
        // For &&: a fact holds in later conjuncts as stated.
        // For ||: later disjuncts run only when earlier ones were FALSE → facts invert.
        Map<String, Kind> known = new HashMap<>();
        for (ExpressionTree operand : operands) {
          List<String> dereferenced = NullFacts.dereferencedIdentifiers(operand);
          // 1) A dereference of a known-null identifier can never succeed.
          for (String name : dereferenced) {
            if (known.get(name) == Kind.NULL) {
              ctx.report(chain, "Impossible condition: '" + name + "' is known to be null "
                  + "here, but this " + (isAnd ? "conjunct" : "disjunct")
                  + " dereferences it — dead code, or a guaranteed NullPointerException.");
              return;
            }
          }
          // 2) A null-comparison contradicting a known fact can never succeed.
          Fact fact = NullFacts.factOf(operand);
          if (fact != null) {
            Kind established = isAnd ? fact.kind() : invert(fact.kind());
            Kind existing = known.get(fact.name());
            if (existing != null && existing != established) {
              ctx.report(chain, "Impossible condition: contradictory null checks on '"
                  + fact.name() + "' — this branch can never execute.");
              return;
            }
            known.put(fact.name(), established);
          }
          // 3) Conservative invalidation: a method call inside the operand may change any
          //    field a simple identifier could refer to — drop everything, record nothing.
          if (NullFacts.containsMethodCall(operand)) {
            known.clear();
            continue;
          }
          // 4) A call-free operand that dereferenced x proves x was non-null (it evaluated
          //    without throwing) — usable by later operands under both && and ||.
          for (String name : dereferenced) {
            known.putIfAbsent(name, Kind.NONNULL);
          }
        }
      }

      private Kind invert(Kind kind) {
        return kind == Kind.NULL ? Kind.NONNULL : Kind.NULL;
      }
    };
  }
}
