package io.codekoll.rules.frameworks;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * CK-RECORD-BINDING-TRAP: a type annotated for a binding contract it cannot meet: a JPA
 * {@code @Entity} record, or a JavaBean-bound {@code @ConfigurationProperties} class with a final
 * scalar field that can never be set.
 */
public final class RecordBindingTrapRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-RECORD-BINDING-TRAP");

  private static final Set<String> BOXED_OR_STRING = Set.of("java.lang.String",
      "java.lang.Boolean", "java.lang.Byte", "java.lang.Character", "java.lang.Short",
      "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "@Entity record, or @ConfigurationProperties field that can never be bound";
  }

  @Override
  public String explanation() {
    return "JPA creates entities through a no-arg constructor and proxies them by subclassing; "
        + "a record is final and has only its canonical constructor, so an @Entity record stops "
        + "the persistence unit from starting. Spring Boot binds a @ConfigurationProperties "
        + "class through setters unless it has a single parameterized constructor or "
        + "@ConstructorBinding; a final field in such a class has no setter, so its property is "
        + "silently ignored and the field keeps its default whatever the configuration says.";
  }

  @Override
  public String fix() {
    return "Make the entity a class with a protected no-arg constructor (records suit DTOs and "
        + "@Embeddable values). For configuration properties, use a record or a single "
        + "constructor (constructor binding), or make the field non-final with a setter.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public @Nullable Void visitClass(ClassTree node, RuleContext ctx) {
        if (node.getKind() == Tree.Kind.RECORD) {
          Annotations.find(node.getModifiers(), "Entity").ifPresent(entity ->
              ctx.report(entity, "A record cannot be a JPA @Entity: it is final and has no "
                  + "no-arg constructor, so the persistence unit fails to start. Use a class, "
                  + "or map the record as an @Embeddable or a DTO projection."));
        } else if (node.getKind() == Tree.Kind.CLASS
            && Annotations.find(node.getModifiers(), "ConfigurationProperties").isPresent()
            && bindsWithSetters(node)) {
          for (Tree member : node.getMembers()) {
            if (member instanceof VariableTree field && isUnbindable(field, ctx)) {
              ctx.report(field, "Spring Boot binds this @ConfigurationProperties class through "
                  + "setters, and final field '" + field.getName() + "' has none: its property "
                  + "is silently ignored. Use constructor binding (a single constructor or a "
                  + "record), or drop final and add a setter.");
            }
          }
        }
        return super.visitClass(node, ctx);
      }

      private boolean isUnbindable(VariableTree field, RuleContext ctx) {
        Set<Modifier> flags = field.getModifiers().getFlags();
        if (!flags.contains(Modifier.FINAL) || flags.contains(Modifier.STATIC)) {
          return false;
        }
        @Nullable TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), field));
        return type != null && (type.getKind().isPrimitive()
            || type.getKind() == TypeKind.DECLARED
                && BOXED_OR_STRING.contains(ctx.qualifiedNameOf(type)));
      }
    };
  }

  /**
   * Spring Boot 3 constructor-binds when a constructor carries {@code @ConstructorBinding}, or
   * when the class has exactly one constructor and it takes parameters (and is not
   * {@code @Autowired}). Otherwise it binds through setters.
   */
  private static boolean bindsWithSetters(ClassTree type) {
    List<MethodTree> constructors = type.getMembers().stream()
        .filter(member -> member instanceof MethodTree method
            && method.getName().contentEquals("<init>"))
        .map(MethodTree.class::cast)
        .toList();
    if (constructors.stream().anyMatch(ctor ->
        Annotations.find(ctor.getModifiers(), "ConstructorBinding").isPresent())) {
      return false;
    }
    boolean singleParameterized = constructors.size() == 1
        && !constructors.getFirst().getParameters().isEmpty()
        && Annotations.find(constructors.getFirst().getModifiers(), "Autowired").isEmpty();
    return !singleParameterized;
  }
}
