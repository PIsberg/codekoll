package io.codekoll.rules.frameworks;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.element.Modifier;

/**
 * CK-INJECT-STATIC: {@code @Autowired}/{@code @Value}/{@code @Inject} on a static field —
 * DI containers skip static fields silently; the field stays null.
 */
public final class InjectStaticRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-INJECT-STATIC");

  private static final Set<String> INJECT_ANNOTATIONS =
      Set.of("Autowired", "Value", "Inject", "Resource", "PersistenceContext");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.FRAMEWORKS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "@Autowired/@Value/@Inject on a static field is skipped by DI";
  }

  @Override
  public String explanation() {
    return "Dependency-injection containers inject INSTANCE state; static fields are "
        + "silently skipped (Spring logs nothing by default). The field keeps its null "
        + "default and the first use throws NullPointerException — far from the "
        + "declaration that looks correctly wired.";
  }

  @Override
  public String fix() {
    return "Make the field an instance field (constructor injection preferred), or set the "
        + "static explicitly from an instance @PostConstruct if a static is unavoidable.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitVariable(VariableTree node, RuleContext ctx) {
        if (node.getModifiers().getFlags().contains(Modifier.STATIC)) {
          for (AnnotationTree annotation : node.getModifiers().getAnnotations()) {
            String name = simpleName(annotation.getAnnotationType());
            if (INJECT_ANNOTATIONS.contains(name)) {
              ctx.report(node, "@" + name + " on a static field is silently skipped by the "
                  + "container — the field stays null. Use instance (constructor) "
                  + "injection.");
              break;
            }
          }
        }
        return super.visitVariable(node, ctx);
      }

      private String simpleName(Tree annotationType) {
        String text = annotationType.toString();
        int dot = text.lastIndexOf('.');
        return dot < 0 ? text : text.substring(dot + 1);
      }
    };
  }
}
