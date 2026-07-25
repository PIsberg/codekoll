package io.codekoll.rules.nullness;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;

/**
 * CK-OPTIONAL-OF-NULLABLE: {@code Optional.of(expr)} where expr is a null literal or a
 * known-nullable call — NPEs exactly when {@code Optional.ofNullable} was intended.
 */
public final class OptionalOfNullableRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-OPTIONAL-OF-NULLABLE");

  private static final Set<String> NULLABLE_METHODS =
      Set.of("get", "getenv", "getProperty", "poll", "peek");

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
    return "Optional.of(possibly-null) instead of ofNullable";
  }

  @Override
  public String explanation() {
    return "Optional.of throws NullPointerException if its argument is null — it is for "
        + "values known to be present. Passing a null literal or a nullable result "
        + "(map.get, System.getenv) defeats the point: the code that was supposed to model "
        + "absence safely crashes on exactly the absent case.";
  }

  @Override
  public String fix() {
    return "Use Optional.ofNullable(expr) when the value may be null; keep Optional.of only "
        + "for values you know are non-null.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 1
            && node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("of")
            && select.getExpression().toString().endsWith("Optional")
            && isPossiblyNull(node.getArguments().get(0))) {
          ctx.report(node, "Optional.of throws if the argument is null. Use "
              + "Optional.ofNullable for a possibly-absent value.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isPossiblyNull(ExpressionTree arg) {
        ExpressionTree e = NullFacts.unwrap(arg);
        if (e.getKind() == Tree.Kind.NULL_LITERAL) {
          return true;
        }
        return e instanceof MethodInvocationTree call
            && call.getMethodSelect() instanceof MemberSelectTree select
            && NULLABLE_METHODS.contains(select.getIdentifier().toString());
      }
    };
  }
}
