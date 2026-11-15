package io.codekoll.rules.deadcode;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/** CK-UNUSED-PRIVATE: a private method or field never used within its compilation unit. */
public final class UnusedPrivateRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-UNUSED-PRIVATE");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.DEAD_CODE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "Private method or field that is never used";
  }

  @Override
  public String explanation() {
    return "A private member can only be used inside its own file, so one that is never "
        + "referenced, or a field that is only written, is dead. Exempt: members used by "
        + "method references or lambdas, annotated members or classes (other than java.lang "
        + "annotations), serialization members, names that appear as string literals, methods "
        + "sharing a name with an annotated method, and record components.";
  }

  @Override
  public String fix() {
    return "Remove the member, or use it.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      private Set<Element> reads = Set.of();
      private Set<Element> writes = new HashSet<>();
      private Set<String> literals = new HashSet<>();

      @Override
      public Void visitCompilationUnit(CompilationUnitTree node, RuleContext ctx) {
        reads = Reads.in(node, ctx.trees());
        writes = new HashSet<>();
        literals = new HashSet<>();
        new TreePathScanner<Void, Void>() {
          @Override
          public Void visitAssignment(AssignmentTree a, Void v) {
            Element e = ctx.trees().getElement(new TreePath(getCurrentPath(), a.getVariable()));
            if (e != null) {
              writes.add(e);
            }
            return super.visitAssignment(a, v);
          }

          @Override
          public Void visitLiteral(LiteralTree l, Void v) {
            if (l.getValue() instanceof String s) {
              literals.add(s);
            }
            return super.visitLiteral(l, v);
          }
        }.scan(new TreePath(node), null);
        return super.visitCompilationUnit(node, ctx);
      }

      @Override
      public Void visitMethod(MethodTree node, RuleContext ctx) {
        Element e = ctx.trees().getElement(getCurrentPath());
        if (e instanceof ExecutableElement m && m.getKind() == ElementKind.METHOD
            && m.getModifiers().contains(Modifier.PRIVATE) && !reads.contains(m)
            && !exempt(m)) {
          ctx.report(node, "Private method '" + m.getSimpleName() + "' is never used");
        }
        return super.visitMethod(node, ctx);
      }

      @Override
      public Void visitVariable(VariableTree node, RuleContext ctx) {
        Element e = ctx.trees().getElement(getCurrentPath());
        if (e != null && e.getKind() == ElementKind.FIELD
            && e.getModifiers().contains(Modifier.PRIVATE) && !reads.contains(e)
            && !exempt(e)) {
          String name = e.getSimpleName().toString();
          ctx.report(node, writes.contains(e)
              ? "Private field '" + name + "' is written but never read"
              : "Private field '" + name + "' is never used");
        }
        return super.visitVariable(node, ctx);
      }

      private boolean exempt(Element e) {
        String name = e.getSimpleName().toString();
        if (literals.contains(name) || hasRealAnnotation(e)) {
          return true;
        }
        for (Element c = e.getEnclosingElement(); c != null; c = c.getEnclosingElement()) {
          if (hasRealAnnotation(c)) {
            return true;
          }
        }
        Element owner = e.getEnclosingElement();
        if (e instanceof ExecutableElement m) {
          if (isSerializationMethod(m)) {
            return true;
          }
          for (Element sibling : owner.getEnclosedElements()) {
            if (!sibling.equals(e) && sibling.getKind() == ElementKind.METHOD
                && sibling.getSimpleName().contentEquals(name)
                && !sibling.getAnnotationMirrors().isEmpty()) {
              return true;
            }
          }
          return false;
        }
        if ("serialVersionUID".equals(name) || "serialPersistentFields".equals(name)) {
          return true;
        }
        return owner instanceof TypeElement t && t.getKind() == ElementKind.RECORD
            && t.getRecordComponents().stream()
                .anyMatch(rc -> rc.getSimpleName().contentEquals(name));
      }

      private boolean hasRealAnnotation(Element e) {
        for (AnnotationMirror a : e.getAnnotationMirrors()) {
          Element at = a.getAnnotationType().asElement();
          if (!(at instanceof TypeElement t)
              || !t.getQualifiedName().toString().startsWith("java.lang.")) {
            return true;
          }
        }
        return false;
      }

      private boolean isSerializationMethod(ExecutableElement m) {
        List<? extends VariableElement> ps = m.getParameters();
        String ret = m.getReturnType().toString();
        return switch (m.getSimpleName().toString()) {
          case "writeObject" -> "void".equals(ret) && ps.size() == 1
              && "java.io.ObjectOutputStream".equals(ps.get(0).asType().toString());
          case "readObject" -> "void".equals(ret) && ps.size() == 1
              && "java.io.ObjectInputStream".equals(ps.get(0).asType().toString());
          case "readObjectNoData" -> "void".equals(ret) && ps.isEmpty();
          case "writeReplace", "readResolve" -> "java.lang.Object".equals(ret) && ps.isEmpty();
          default -> false;
        };
      }
    };
  }
}
