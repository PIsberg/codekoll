package io.codekoll.rules.apimisuse;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.jspecify.annotations.Nullable;

/**
 * CK-IMMUTABLE-FACTORY-DUPLICATE: the same constant appears twice as a {@code Set.of} element
 * or a {@code Map.of}/{@code Map.ofEntries} key. Both factories reject duplicates at
 * construction, so the call always throws {@code IllegalArgumentException}.
 */
public final class ImmutableFactoryDuplicateRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-IMMUTABLE-FACTORY-DUPLICATE");

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
    return "Duplicate constant element in Set.of or duplicate key in Map.of — always throws";
  }

  @Override
  public String explanation() {
    return "Unlike the collections they replace, Set.of and Map.of refuse to swallow a "
        + "duplicate: they throw IllegalArgumentException (\"duplicate element\" / "
        + "\"duplicate key\") while the collection is being built. new HashSet<>() and "
        + "HashMap.put quietly keep the last value, so the same copy-paste in older code was "
        + "invisible. Here it is fatal, and because these tables are usually static final "
        + "fields the failure arrives as ExceptionInInitializerError during class loading, "
        + "long before any test touches the duplicated entry.";
  }

  @Override
  public String fix() {
    return "Remove the repeated element or key — or, if both entries are wanted and the last "
        + "one should win, build the collection with new HashSet<>()/new HashMap<>() instead.";
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
        if (!(node.getMethodSelect() instanceof MemberSelectTree select)) {
          return;
        }
        String method = select.getIdentifier().toString();
        String receiver = receiverType(select, ctx);
        if ("of".equals(method) && "java.util.Set".equals(receiver)) {
          reportFirstDuplicate(node.getArguments(), "element", "Set.of", ctx);
        } else if ("of".equals(method) && "java.util.Map".equals(receiver)) {
          reportFirstDuplicate(everyOther(node.getArguments()), "key", "Map.of", ctx);
        } else if ("ofEntries".equals(method) && "java.util.Map".equals(receiver)) {
          reportFirstDuplicate(entryKeys(node.getArguments(), ctx), "key", "Map.ofEntries", ctx);
        }
      }

      private void reportFirstDuplicate(List<? extends ExpressionTree> candidates, String what,
          String factory, RuleContext ctx) {
        Set<String> seen = new HashSet<>();
        for (ExpressionTree candidate : candidates) {
          String token = constantToken(candidate, ctx);
          if (token != null && !seen.add(token)) {
            ctx.report(candidate, factory + " rejects duplicates — this " + what + " is already "
                + "listed above, so the call throws IllegalArgumentException every time it "
                + "runs. Remove the repeat, or build the collection with new HashSet<>()/"
                + "new HashMap<>() if the last entry should win.");
            return;
          }
        }
      }

      /** Map.of takes k1, v1, k2, v2, …: the keys are the even positions. */
      private List<ExpressionTree> everyOther(List<? extends ExpressionTree> args) {
        List<ExpressionTree> keys = new ArrayList<>();
        for (int i = 0; i + 1 < args.size(); i += 2) {
          keys.add(args.get(i));
        }
        return keys;
      }

      /** First argument of each {@code Map.entry(k, v)} in a Map.ofEntries call. */
      private List<ExpressionTree> entryKeys(List<? extends ExpressionTree> args,
          RuleContext ctx) {
        List<ExpressionTree> keys = new ArrayList<>();
        for (ExpressionTree arg : args) {
          if (unwrap(arg) instanceof MethodInvocationTree call
              && call.getMethodSelect() instanceof MemberSelectTree select
              && "entry".equals(select.getIdentifier().toString())
              && "java.util.Map".equals(receiverType(select, ctx))
              && !call.getArguments().isEmpty()) {
            keys.add(call.getArguments().get(0));
          }
        }
        return keys;
      }

      private @Nullable String receiverType(MemberSelectTree select, RuleContext ctx) {
        Element element =
            ctx.trees().getElement(new TreePath(getCurrentPath(), select.getExpression()));
        return element instanceof TypeElement type ? type.getQualifiedName().toString() : null;
      }

      /**
       * Identity of a compile-time-known value: literals and constant fields by type+value,
       * enum constants by their qualified name. Null when the value is not known statically.
       */
      private @Nullable String constantToken(ExpressionTree expr, RuleContext ctx) {
        ExpressionTree e = unwrap(expr);
        if (e instanceof LiteralTree literal && literal.getValue() != null) {
          Object value = literal.getValue();
          return value.getClass().getName() + ':' + value;
        }
        Element element = ctx.trees().getElement(new TreePath(getCurrentPath(), e));
        if (element instanceof VariableElement variable) {
          if (variable.getKind() == ElementKind.ENUM_CONSTANT) {
            return "enum:" + variable.getEnclosingElement() + '.' + variable.getSimpleName();
          }
          Object constant = variable.getConstantValue();
          if (constant != null) {
            return constant.getClass().getName() + ':' + constant;
          }
        }
        return null;
      }

      private ExpressionTree unwrap(ExpressionTree expr) {
        ExpressionTree e = expr;
        while (e instanceof ParenthesizedTree parens) {
          e = parens.getExpression();
        }
        return e;
      }
    };
  }
}
