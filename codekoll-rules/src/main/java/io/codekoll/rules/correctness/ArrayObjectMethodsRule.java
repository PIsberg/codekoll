package io.codekoll.rules.correctness;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Map;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * CK-ARRAY-OBJECT-METHODS: {@code equals}/{@code hashCode}/{@code toString} invoked on an
 * array — arrays inherit Object's identity implementations, so contents are never compared
 * and printing yields {@code [Ljava.lang.String;@1a2b3c}.
 */
public final class ArrayObjectMethodsRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-ARRAY-OBJECT-METHODS");

  private static final Map<String, String> SUGGESTIONS = Map.of(
      "equals", "Arrays.equals(a, b) (or Arrays.deepEquals for nested arrays)",
      "hashCode", "Arrays.hashCode(a)",
      "toString", "Arrays.toString(a)");

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
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "equals/hashCode/toString on an array uses identity, not contents";
  }

  @Override
  public String explanation() {
    return "Arrays do not override Object's methods: a.equals(b) is reference identity "
        + "(two arrays with identical contents are NOT equal), hashCode ignores contents, "
        + "and toString prints '[Ljava.lang.String;@1a2b3c'. Content-based comparisons and "
        + "log output silently do the wrong thing.";
  }

  @Override
  public String fix() {
    return "Use java.util.Arrays: Arrays.equals / Arrays.hashCode / Arrays.toString "
        + "(deep* variants for nested arrays).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && node.getArguments().size() <= 1) {
          String name = select.getIdentifier().toString();
          String suggestion = SUGGESTIONS.get(name);
          if (suggestion != null && isArray(select, ctx)
              && ("equals".equals(name) == (node.getArguments().size() == 1))) {
            ctx.report(node, name + "() on an array compares/prints identity, never "
                + "contents. Use " + suggestion + ".");
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isArray(MemberSelectTree select, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
        return type != null && type.getKind() == TypeKind.ARRAY;
      }
    };
  }
}
