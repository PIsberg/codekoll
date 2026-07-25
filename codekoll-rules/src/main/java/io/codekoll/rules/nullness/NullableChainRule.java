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
import java.util.Map;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * CK-NULLABLE-CHAIN: a member access chained directly onto a known-nullable-returning call —
 * {@code map.get(k).run()}, {@code System.getenv("X").trim()} — NPEs on the absent case.
 */
public final class NullableChainRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-NULLABLE-CHAIN");

  /** method name → required receiver (empty = static/any). Shared with CK-UNBOX-NPE intent. */
  private static final Map<String, String> NULLABLE_METHODS = Map.ofEntries(
      Map.entry("get", "java.util.Map"),
      Map.entry("poll", "java.util.Queue"),
      Map.entry("peek", "java.util.Queue"),
      Map.entry("getenv", "java.lang.System"),
      Map.entry("getProperty", "java.lang.System"),
      Map.entry("group", "java.util.regex.Matcher"));

  private static final Set<String> STATIC_NULLABLE = Set.of("getenv", "getProperty");

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
    return "Method call chained onto a known-nullable result";
  }

  @Override
  public String explanation() {
    return "Map.get, System.getenv, Matcher.group and similar return null when the entry is "
        + "absent. Chaining a call directly onto the result — get(k).trim() — dereferences "
        + "that null and throws NullPointerException on exactly the miss case, which is "
        + "usually the untested path.";
  }

  @Override
  public String fix() {
    return "Store the result, null-check it (or use Optional.ofNullable / getOrDefault), "
        + "then call through.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMemberSelect(MemberSelectTree node, RuleContext ctx) {
        if (NullFacts.unwrap(node.getExpression()) instanceof MethodInvocationTree producer
            && isNullableCall(producer, ctx)) {
          ctx.report(node, "'" + producer + "' can return null (miss/absent); the chained "
              + "." + node.getIdentifier() + " then NPEs. Null-check the result first.");
        }
        return super.visitMemberSelect(node, ctx);
      }

      private boolean isNullableCall(MethodInvocationTree call, RuleContext ctx) {
        if (!(call.getMethodSelect() instanceof MemberSelectTree select)) {
          return false;
        }
        String method = select.getIdentifier().toString();
        String required = NULLABLE_METHODS.get(method);
        if (required == null) {
          return false;
        }
        if (STATIC_NULLABLE.contains(method)) {
          return select.getExpression().toString().endsWith("System");
        }
        TypeMirror receiver = receiverType(select, ctx);
        return ctx.isSubtypeOf(receiver, required);
      }

      private TypeMirror receiverType(MemberSelectTree select, RuleContext ctx) {
        return ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
      }
    };
  }
}
