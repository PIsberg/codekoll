package io.codekoll.rules.frameworks;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
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
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * CK-SEALED-SUBTYPES-DRIFT: a sealed type whose {@code @JsonSubTypes}/{@code @XmlSeeAlso} list
 * omits a permitted subtype. javac checks the permits clause against switches, never against the
 * annotation, so a new subtype compiles and then fails to deserialize.
 */
public final class SealedSubtypesDriftRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-SEALED-SUBTYPES-DRIFT");

  private static final List<String> REGISTRIES = List.of("JsonSubTypes", "XmlSeeAlso");

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
    return "@JsonSubTypes/@XmlSeeAlso on a sealed type omits a permitted subtype";
  }

  @Override
  public String explanation() {
    return "The compiler keeps switches over a sealed type in step with its permits clause, but "
        + "nothing checks the subtype list in @JsonSubTypes or @XmlSeeAlso. Adding a permitted "
        + "subtype compiles and serializes, and reading it back fails at runtime with "
        + "InvalidTypeIdException (Could not resolve type id), typically in another service or "
        + "after a deploy, when the first such message arrives.";
  }

  @Override
  public String fix() {
    return "Add the missing subtypes to the annotation, or drop the list and use "
        + "@JsonTypeInfo(use = Id.CLASS) if the class name may appear in the payload.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public @Nullable Void visitClass(ClassTree node, RuleContext ctx) {
        @Nullable Element element = ctx.trees().getElement(getCurrentPath());
        if (element instanceof TypeElement type && type.getModifiers().contains(Modifier.SEALED)
            && !serializesClassName(node)) {
          for (String registry : REGISTRIES) {
            Annotations.find(node.getModifiers(), registry)
                .ifPresent(annotation -> check(annotation, type, ctx));
          }
        }
        return super.visitClass(node, ctx);
      }

      private void check(AnnotationTree annotation, TypeElement sealed, RuleContext ctx) {
        Set<Element> listed = listedTypes(new TreePath(getCurrentPath(), annotation), ctx);
        List<String> missing = new ArrayList<>();
        for (TypeMirror permitted : sealed.getPermittedSubclasses()) {
          if (ctx.types().asElement(permitted) instanceof TypeElement subtype) {
            collectMissing(subtype, listed, ctx, missing);
          }
        }
        if (!missing.isEmpty()) {
          ctx.report(annotation, "@" + Annotations.simpleName(annotation.getAnnotationType())
              + " on sealed " + sealed.getSimpleName() + " omits permitted subtype(s) "
              + String.join(", ", missing) + ": reading them back fails with "
              + "InvalidTypeIdException. Add them to the list.");
        }
      }
    };
  }

  /** {@code @JsonTypeInfo(use = Id.CLASS)} or {@code MINIMAL_CLASS}: no list needed. */
  private static boolean serializesClassName(ClassTree node) {
    Optional<AnnotationTree> info = Annotations.find(node.getModifiers(), "JsonTypeInfo");
    return info.isPresent() && info.get().getArguments().stream()
        .anyMatch(arg -> arg instanceof AssignmentTree assignment
            && assignment.getVariable() instanceof IdentifierTree name
            && name.getName().contentEquals("use")
            && assignment.getExpression().toString().endsWith("CLASS"));
  }

  /** Every {@code X.class} literal anywhere in the annotation's arguments. */
  private static Set<Element> listedTypes(TreePath annotation, RuleContext ctx) {
    Set<Element> listed = new HashSet<>();
    new TreePathScanner<Void, Void>() {
      @Override
      public @Nullable Void visitMemberSelect(MemberSelectTree node, Void unused) {
        if (node.getIdentifier().contentEquals("class")) {
          ExpressionTree type = node.getExpression();
          @Nullable Element element = ctx.trees().getElement(new TreePath(getCurrentPath(), type));
          if (element != null) {
            listed.add(element);
          }
        }
        return super.visitMemberSelect(node, unused);
      }
    }.scan(annotation, null);
    return listed;
  }

  /**
   * A concrete subtype must be listed. An abstract or interface subtype is covered when listed,
   * or when sealed and all of its own permitted subtypes are covered; a non-sealed abstract one
   * cannot be enumerated and is treated as covered.
   */
  private static void collectMissing(TypeElement subtype, Set<Element> listed, RuleContext ctx,
      List<String> missing) {
    if (listed.contains(subtype)) {
      return;
    }
    boolean isAbstract = subtype.getKind() == ElementKind.INTERFACE
        || subtype.getModifiers().contains(Modifier.ABSTRACT);
    if (!isAbstract) {
      missing.add(subtype.getSimpleName().toString());
      return;
    }
    if (subtype.getModifiers().contains(Modifier.SEALED)) {
      for (TypeMirror permitted : subtype.getPermittedSubclasses()) {
        if (ctx.types().asElement(permitted) instanceof TypeElement nested) {
          collectMissing(nested, listed, ctx, missing);
        }
      }
    }
  }
}
