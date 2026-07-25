package io.codekoll.rules.correctness;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;

/**
 * CK-EQUALS-HASHCODE: a class overrides {@code equals(Object)} without {@code hashCode()}
 * (or vice versa) — breaks every hash-based collection.
 */
public final class EqualsHashcodeRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-EQUALS-HASHCODE");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "equals without hashCode (or hashCode without equals)";
  }

  @Override
  public String explanation() {
    return "HashMap/HashSet locate an object by hashCode FIRST, then confirm with equals. "
        + "With only equals overridden, two equal objects land in different buckets: "
        + "set.contains(equalCopy) is false, map lookups miss, duplicates accumulate. "
        + "It works in the tests that use the same instance and fails on real data.";
  }

  @Override
  public String fix() {
    return "Override both, from the same fields (Objects.hash / Objects.equals) — or make "
        + "the class a record and get both for free.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitClass(ClassTree node, RuleContext ctx) {
        if (node.getKind() == Tree.Kind.CLASS) {
          boolean hasEquals = false;
          boolean hasHashCode = false;
          for (Tree member : node.getMembers()) {
            if (member instanceof MethodTree method && method.getBody() != null) {
              if (method.getName().contentEquals("equals")
                  && method.getParameters().size() == 1) {
                hasEquals = true;
              } else if (method.getName().contentEquals("hashCode")
                  && method.getParameters().isEmpty()) {
                hasHashCode = true;
              }
            }
          }
          if (hasEquals != hasHashCode) {
            ctx.report(node, "Class overrides " + (hasEquals ? "equals" : "hashCode")
                + " but not " + (hasEquals ? "hashCode" : "equals")
                + " — hash-based collections will misbehave. Override both from the "
                + "same fields.");
          }
        }
        return super.visitClass(node, ctx);
      }
    };
  }
}
