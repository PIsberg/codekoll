package io.codekoll.rules.apimisuse;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import org.jspecify.annotations.Nullable;

/**
 * CK-IMMUTABLE-MUTATE: a mutator call on a value that provably originates from
 * {@code List.of}/{@code Map.of}/{@code Collections.unmodifiable*}/{@code Arrays.asList} —
 * guaranteed UnsupportedOperationException.
 */
public final class ImmutableMutateRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-IMMUTABLE-MUTATE");

  private static final Set<String> MUTATORS = Set.of(
      "add", "remove", "set", "put", "clear", "addAll", "removeAll", "retainAll",
      "putAll", "sort", "replaceAll", "removeIf");

  /** Factory method names that produce immutable/fixed-size collections. */
  private static final Set<String> IMMUTABLE_FACTORIES = Set.of(
      "of", "copyOf", "emptyList", "emptyMap", "emptySet", "unmodifiableList",
      "unmodifiableMap", "unmodifiableSet", "unmodifiableCollection", "asList");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.API_MISUSE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Mutating an immutable/fixed-size collection";
  }

  @Override
  public String explanation() {
    return "List.of, Map.of, Collections.unmodifiable* and Arrays.asList return collections "
        + "that reject structural changes: any add/remove/put throws "
        + "UnsupportedOperationException. It compiles (the static type is still List), and "
        + "fails on the first modification — often only on the code path that actually adds "
        + "an element.";
  }

  @Override
  public String fix() {
    return "Wrap in a mutable copy when you need to modify: new ArrayList<>(List.of(...)); "
        + "keep the immutable form where it is only read.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && MUTATORS.contains(select.getIdentifier().toString())
            && originatesFromImmutable(select.getExpression(), ctx)) {
          ctx.report(node, select.getIdentifier() + "() on an immutable/fixed-size "
              + "collection throws UnsupportedOperationException. Copy it into a mutable "
              + "collection first.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      /** Direct immutable factory call, or a local initialised from one. */
      private boolean originatesFromImmutable(ExpressionTree receiver, RuleContext ctx) {
        if (isImmutableFactoryCall(receiver)) {
          return true;
        }
        Element symbol = ctx.trees().getElement(new TreePath(getCurrentPath(), receiver));
        if (symbol != null && symbol.getKind() == ElementKind.LOCAL_VARIABLE) {
          VariableTree decl = declarationOf(symbol, ctx);
          return decl != null && decl.getInitializer() != null
              && isImmutableFactoryCall(decl.getInitializer());
        }
        return false;
      }

      private boolean isImmutableFactoryCall(ExpressionTree expr) {
        if (!(NullFacts.unwrap(expr) instanceof MethodInvocationTree call)
            || !(call.getMethodSelect() instanceof MemberSelectTree select)) {
          return false;
        }
        if (!IMMUTABLE_FACTORIES.contains(select.getIdentifier().toString())) {
          return false;
        }
        String owner = select.getExpression().toString();
        return owner.endsWith("List") || owner.endsWith("Set") || owner.endsWith("Map")
            || owner.endsWith("Collections") || owner.endsWith("Arrays");
      }

      private @Nullable VariableTree declarationOf(Element symbol, RuleContext ctx) {
        return ctx.trees().getTree(symbol) instanceof VariableTree v ? v : null;
      }
    };
  }
}
