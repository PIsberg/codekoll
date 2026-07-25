package io.codekoll.rules.nullness;

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
import java.util.Map;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * CK-UNBOX-NPE: auto-unboxing applied directly to a nullable-by-contract call —
 * {@code int x = map.get(k);} NPEs on every cache miss.
 *
 * <p>The known-nullable method list is shared machinery for the nullness pack.
 */
public final class UnboxNpeRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-UNBOX-NPE");

  /** method simple name → receiver FQN prefix that makes its return nullable-by-contract. */
  static final Map<String, String> NULLABLE_METHODS = Map.ofEntries(
      Map.entry("get", "java.util.Map"),
      Map.entry("getOrDefault", ""),  // only nullable if default is null; not tracked
      Map.entry("poll", "java.util.Queue"),
      Map.entry("peek", "java.util.Queue"),
      Map.entry("getenv", "java.lang.System"),
      Map.entry("getProperty", "java.lang.System"));

  private static final Set<String> PRIMITIVE_TARGETS =
      Set.of("int", "long", "double", "float", "short", "byte", "char", "boolean");

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
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Auto-unboxing of a nullable Map.get/poll result";
  }

  @Override
  public String explanation() {
    return "Map.get returns null on a miss; assigning it to a primitive auto-unboxes with "
        + "value.intValue() — a NullPointerException on exactly the miss path, which is "
        + "the path tests rarely cover. The NPE points at an innocent-looking assignment "
        + "with no visible dereference.";
  }

  @Override
  public String fix() {
    return "Assign to the boxed type and null-check, or use getOrDefault with a real "
        + "default: int x = map.getOrDefault(k, 0);";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitVariable(VariableTree node, RuleContext ctx) {
        if (node.getInitializer() != null
            && PRIMITIVE_TARGETS.contains(node.getType().toString())
            && NullFacts.unwrap(node.getInitializer()) instanceof MethodInvocationTree call
            && isNullableByContract(call, ctx)) {
          ctx.report(node, "The call returns null on the miss/absent case; unboxing into '"
              + node.getType() + "' throws NullPointerException exactly then. Use the "
              + "boxed type + null check, or getOrDefault.");
        }
        return super.visitVariable(node, ctx);
      }

      private boolean isNullableByContract(MethodInvocationTree call, RuleContext ctx) {
        if (!(call.getMethodSelect() instanceof MemberSelectTree select)) {
          return false;
        }
        String method = select.getIdentifier().toString();
        String requiredReceiver = NULLABLE_METHODS.get(method);
        if (requiredReceiver == null || requiredReceiver.isEmpty()) {
          return false;
        }
        TypeMirror receiver =
            ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
        if (receiver == null) {
          // Static call like System.getenv: receiver is the type name itself.
          return select.getExpression().toString().endsWith("System")
              && ("getenv".equals(method) || "getProperty".equals(method));
        }
        return ctx.isSubtypeOf(receiver, requiredReceiver);
      }
    };
  }
}
