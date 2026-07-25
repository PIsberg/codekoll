package io.codekoll.rules.nullness;

import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

/**
 * CK-OVERRIDE-NULLNESS: an override weakens the supertype's nullness contract — a parameter
 * becomes @NonNull where the super was @Nullable, or the return becomes @Nullable where the
 * super was @NonNull. Fires only when the relevant annotations are present.
 */
public final class OverrideNullnessRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-OVERRIDE-NULLNESS");

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
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Override weakens the supertype's nullness contract";
  }

  @Override
  public String explanation() {
    return "Callers dispatching through the supertype rely on ITS annotations. An override "
        + "that accepts fewer nulls (@NonNull param where the super was @Nullable) or "
        + "returns more (@Nullable return where the super was @NonNull) breaks that "
        + "contract silently — the compiler checks the override signature, not its nullness.";
  }

  @Override
  public String fix() {
    return "Keep the override at least as permissive on parameters and at least as strict "
        + "on the return as the supertype method.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethod(MethodTree node, RuleContext ctx) {
        Element element = ctx.trees().getElement(getCurrentPath());
        if (element instanceof ExecutableElement method) {
          ExecutableElement overridden = findOverridden(method, ctx);
          if (overridden != null) {
            checkReturn(node, method, overridden, ctx);
            checkParams(node, method, overridden, ctx);
          }
        }
        return super.visitMethod(node, ctx);
      }

      private void checkReturn(MethodTree node, ExecutableElement method,
          ExecutableElement overridden, RuleContext ctx) {
        // Return: weakening = @Nullable here where super is @NonNull. (TYPE_USE → on type.)
        if (isNullable(method.getReturnType()) && isNonNull(overridden.getReturnType())) {
          ctx.report(node, "Override returns @Nullable but '" + overridden.getSimpleName()
              + "' promises @NonNull — callers through the supertype will not null-check.");
        }
      }

      private void checkParams(MethodTree node, ExecutableElement method,
          ExecutableElement overridden, RuleContext ctx) {
        List<? extends VariableElement> here = method.getParameters();
        List<? extends VariableElement> superParams = overridden.getParameters();
        for (int i = 0; i < here.size() && i < superParams.size(); i++) {
          // Param: weakening = @NonNull here where super is @Nullable.
          if (isNonNull(here.get(i).asType()) && isNullable(superParams.get(i).asType())) {
            ctx.report(node.getParameters().get(i), "Parameter '"
                + here.get(i).getSimpleName() + "' is @NonNull but the overridden method "
                + "accepts @Nullable — a null valid for the supertype crashes here.");
          }
        }
      }

      private @Nullable ExecutableElement findOverridden(ExecutableElement method,
          RuleContext ctx) {
        Element enclosing = method.getEnclosingElement();
        if (!(enclosing instanceof javax.lang.model.element.TypeElement type)) {
          return null;
        }
        Types types = ctx.types();
        for (TypeMirror supertype : types.directSupertypes(type.asType())) {
          if (supertype instanceof DeclaredType declared) {
            for (Element member : declared.asElement().getEnclosedElements()) {
              if (member instanceof ExecutableElement candidate
                  && ctx.elements().overrides(method, candidate, type)) {
                return candidate;
              }
            }
          }
        }
        return null;
      }

      // JSpecify annotations are TYPE_USE, so they live on the TypeMirror, not the element.
      private boolean isNullable(TypeMirror type) {
        return hasAnnotationNamed(type, "Nullable");
      }

      private boolean isNonNull(TypeMirror type) {
        return hasAnnotationNamed(type, "NonNull")
            || hasAnnotationNamed(type, "Nonnull")
            || hasAnnotationNamed(type, "NotNull");
      }

      private boolean hasAnnotationNamed(TypeMirror type, String simpleName) {
        for (AnnotationMirror a : type.getAnnotationMirrors()) {
          if (a.getAnnotationType().asElement().getSimpleName().contentEquals(simpleName)) {
            return true;
          }
        }
        return false;
      }
    };
  }
}
