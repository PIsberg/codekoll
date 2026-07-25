package io.codekoll.rules.frameworks;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.element.Modifier;

/**
 * CK-ENTITY-CONTRACT: a JPA {@code @Entity} class that is final or lacks an accessible
 * no-arg constructor — Hibernate proxying and hydration fail at runtime.
 */
public final class EntityContractRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-ENTITY-CONTRACT");

  private static final Set<String> ENTITY_ANNOTATIONS =
      Set.of("Entity", "Embeddable", "MappedSuperclass");

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
    return "JPA @Entity that is final or has no no-arg constructor";
  }

  @Override
  public String explanation() {
    return "JPA providers instantiate entities reflectively via a no-arg constructor and "
        + "subclass them for lazy-loading proxies. A final entity cannot be proxied, and one "
        + "with only a parameterised constructor cannot be instantiated — both fail at "
        + "runtime with provider-specific errors far from this class, often only when a lazy "
        + "association is first touched.";
  }

  @Override
  public String fix() {
    return "Make the class non-final and add a (package-private or protected) no-arg "
        + "constructor alongside any parameterised ones.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitClass(ClassTree node, RuleContext ctx) {
        if (node.getKind() == Tree.Kind.CLASS && isEntity(node)) {
          if (node.getModifiers().getFlags().contains(Modifier.FINAL)) {
            ctx.report(node, "@Entity class is final — JPA cannot create a lazy-loading "
                + "proxy. Make it non-final.");
          } else if (!hasAccessibleNoArgCtor(node)) {
            ctx.report(node, "@Entity class has no accessible no-arg constructor — JPA "
                + "cannot instantiate it. Add one (package-private or protected).");
          }
        }
        return super.visitClass(node, ctx);
      }

      private boolean isEntity(ClassTree cls) {
        for (AnnotationTree annotation : cls.getModifiers().getAnnotations()) {
          String text = annotation.getAnnotationType().toString();
          if (ENTITY_ANNOTATIONS.contains(text.substring(text.lastIndexOf('.') + 1))) {
            return true;
          }
        }
        return false;
      }

      private boolean hasAccessibleNoArgCtor(ClassTree cls) {
        boolean anyCtor = false;
        for (Tree member : cls.getMembers()) {
          if (member instanceof MethodTree method
              && method.getName().contentEquals("<init>")) {
            anyCtor = true;
            if (method.getParameters().isEmpty()
                && !method.getModifiers().getFlags().contains(Modifier.PRIVATE)) {
              return true;
            }
          }
        }
        // No explicit constructors at all → the compiler-generated default one exists.
        return !anyCtor;
      }
    };
  }
}
