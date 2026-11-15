package io.codekoll.rules.frameworks;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

/**
 * Annotation lookups by simple name, so rules work whether or not the framework is on the
 * classpath (the pack's attribution tolerance, SPEC section 6.10).
 */
final class Annotations {

  private Annotations() {}

  /** The annotation named {@code simpleName} on these modifiers, if present. */
  static Optional<AnnotationTree> find(ModifiersTree modifiers, String simpleName) {
    return modifiers.getAnnotations().stream()
        .filter(annotation -> simpleName(annotation.getAnnotationType()).equals(simpleName))
        .<AnnotationTree>map(annotation -> annotation)
        .findFirst();
  }

  /** Whether the resolved element carries an annotation named {@code simpleName}. */
  static boolean has(Element element, String simpleName) {
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      if (mirror.getAnnotationType().asElement().getSimpleName().contentEquals(simpleName)) {
        return true;
      }
    }
    return false;
  }

  /** The literal boolean value of {@code attribute}, or empty when absent or not a literal. */
  static Optional<Boolean> booleanAttribute(AnnotationTree annotation, String attribute) {
    for (ExpressionTree argument : annotation.getArguments()) {
      if (argument instanceof AssignmentTree assignment
          && assignment.getVariable() instanceof IdentifierTree name
          && name.getName().contentEquals(attribute)
          && assignment.getExpression() instanceof LiteralTree literal
          && literal.getValue() instanceof Boolean value) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }

  /** {@code Transactional} for {@code @org.springframework...Transactional} or {@code @Outer.Transactional}. */
  static String simpleName(Tree annotationType) {
    String text = annotationType.toString();
    return text.substring(text.lastIndexOf('.') + 1);
  }
}
