package io.codekoll.rules.apimisuse;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * CK-IMMUTABLE-FACTORY-NULL: a {@code null} literal passed to {@code List.of}, {@code Set.of},
 * {@code Map.of}, {@code Map.entry}, {@code Map.ofEntries} or {@code …copyOf}. The Java 9
 * immutable-collection factories reject null outright, so the call always throws.
 */
public final class ImmutableFactoryNullRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-IMMUTABLE-FACTORY-NULL");

  private static final Set<String> FACTORY_TYPES =
      Set.of("java.util.List", "java.util.Set", "java.util.Map");

  private static final Set<String> FACTORY_METHODS = Set.of("of", "ofEntries", "entry", "copyOf");

  /** Simple name of the receiver type → what the author should reach for instead. */
  private static final Map<String, String> ALTERNATIVE = Map.of(
      "List", "Arrays.asList(...) or a new ArrayList<>()",
      "Set", "a new HashSet<>()",
      "Map", "a new HashMap<>()");

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
    return "null passed to List.of/Set.of/Map.of — these factories reject null and always throw";
  }

  @Override
  public String explanation() {
    return "The Java 9 immutable-collection factories forbid null elements, keys and values by "
        + "contract: List.of(a, null) throws NullPointerException immediately, before the list "
        + "even exists. Nothing in the type system says so, so the call compiles cleanly and "
        + "fails on the first execution of that line — often in a static initializer, where the "
        + "NPE surfaces as an ExceptionInInitializerError or NoClassDefFoundError pointing at a "
        + "class that looks unrelated. The older factories (Arrays.asList, "
        + "Collections.singletonList, HashMap) do permit null, which is why the habit survives.";
  }

  @Override
  public String fix() {
    return "Drop the null, or hold the value in a collection that permits null — "
        + "Arrays.asList(...), new ArrayList<>(), new HashMap<>(). For an absent map value "
        + "prefer leaving the key out entirely.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        check(node, ctx);
        return super.visitMethodInvocation(node, ctx);
      }

      private void check(MethodInvocationTree node, RuleContext ctx) {
        if (!(node.getMethodSelect() instanceof MemberSelectTree select)
            || !FACTORY_METHODS.contains(select.getIdentifier().toString())) {
          return;
        }
        String receiver = factoryReceiver(select, ctx);
        if (receiver == null) {
          return;
        }
        for (ExpressionTree arg : node.getArguments()) {
          if (isNullLiteral(arg)) {
            String simple = receiver.substring(receiver.lastIndexOf('.') + 1);
            ctx.report(arg, simple + "." + select.getIdentifier() + " rejects null by contract — "
                + "this call throws NullPointerException every time it runs. Drop the null or "
                + "use " + ALTERNATIVE.get(simple) + ", which permit null.");
            return;
          }
        }
      }

      /** Qualified name when the receiver is one of the three factory types, else null. */
      private String factoryReceiver(MemberSelectTree select, RuleContext ctx) {
        Element element =
            ctx.trees().getElement(new TreePath(getCurrentPath(), select.getExpression()));
        if (!(element instanceof TypeElement type)) {
          return null;
        }
        String qualified = type.getQualifiedName().toString();
        return FACTORY_TYPES.contains(qualified) ? qualified : null;
      }

      /** A bare {@code null}, or one dressed up as {@code (String) null}. */
      private boolean isNullLiteral(ExpressionTree expr) {
        ExpressionTree e = expr;
        while (true) {
          if (e instanceof ParenthesizedTree parens) {
            e = parens.getExpression();
          } else if (e instanceof TypeCastTree cast) {
            e = cast.getExpression();
          } else {
            return e.getKind() == Tree.Kind.NULL_LITERAL;
          }
        }
      }
    };
  }
}
