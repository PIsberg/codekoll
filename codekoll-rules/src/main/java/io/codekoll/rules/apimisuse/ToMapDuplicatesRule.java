package io.codekoll.rules.apimisuse;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * CK-TOMAP-DUPLICATES: two-argument {@code Collectors.toMap} throws
 * {@code IllegalStateException: Duplicate key} on the first key collision — typically
 * discovered on production data.
 */
public final class ToMapDuplicatesRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-TOMAP-DUPLICATES");

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
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "Collectors.toMap without a merge function throws on duplicate keys";
  }

  @Override
  public String explanation() {
    return "The two-argument toMap throws IllegalStateException the FIRST time two stream "
        + "elements map to the same key. Test data rarely has duplicates; production data "
        + "eventually does — the pipeline that ran for months crashes on the day two "
        + "customers share an email.";
  }

  @Override
  public String fix() {
    return "State the merge policy explicitly: toMap(keyFn, valueFn, (a, b) -> a) — or "
        + "use groupingBy when duplicates are expected data.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() == 2
            && node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("toMap")
            && isCollectors(select, ctx)) {
          ctx.report(node, "Two-arg toMap throws IllegalStateException on the first "
              + "duplicate key. Add the merge function: toMap(k, v, (a, b) -> a).");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isCollectors(MemberSelectTree select, RuleContext ctx) {
        Element element = ctx.trees().getElement(
            new TreePath(getCurrentPath(), select.getExpression()));
        return element instanceof TypeElement type
            && "java.util.stream.Collectors".equals(type.getQualifiedName().toString());
      }
    };
  }
}
