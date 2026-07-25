package io.codekoll.rules.nullness;

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
 * CK-OPTIONAL-GET-BARE: {@code .get()}/{@code .orElseThrow()} chained directly onto an
 * Optional-returning call with no guard — collapses Optional back into an unchecked
 * NPE-equivalent. Only direct chains fire (v1).
 */
public final class OptionalGetBareRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-OPTIONAL-GET-BARE");

  private static final Set<String> BARE_GETTERS = Set.of("get", "orElseThrow");

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
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "Optional.get() chained directly onto the producing call";
  }

  @Override
  public String explanation() {
    return "repo.find(id).get() asserts the value is always present — the exact assumption "
        + "Optional was introduced to make explicit and checked. On the absent case it "
        + "throws NoSuchElementException: the same crash a null would have caused, minus "
        + "the API's protection. The chain says 'this cannot be empty' without handling "
        + "'but what if it is'.";
  }

  @Override
  public String fix() {
    return "State the absent case: .orElse(default), .orElseThrow(() -> new "
        + "NotFoundException(id)), or .map(...)/.ifPresent(...).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().isEmpty()
            && node.getMethodSelect() instanceof MemberSelectTree select
            && BARE_GETTERS.contains(select.getIdentifier().toString())
            && NullFacts.unwrap(select.getExpression()) instanceof MethodInvocationTree producer
            && isOptional(producer, ctx)) {
          ctx.report(node, "." + select.getIdentifier() + "() directly on the producing "
              + "call asserts presence without handling absence — NoSuchElementException "
              + "on the empty case. State the absent case (orElse / orElseThrow(...) / "
              + "map).");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isOptional(MethodInvocationTree producer, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), producer));
        String name = ctx.qualifiedNameOf(type);
        return name.startsWith("java.util.Optional");
      }
    };
  }
}
