package io.codekoll.rules.modern;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;

/**
 * CK-RECORD-MUTABLE-COMPONENT: a record component of a mutable collection/date type with no
 * defensive copy in a compact constructor — callers can mutate the record's state.
 */
public final class RecordMutableComponentRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-RECORD-MUTABLE-COMPONENT");

  private static final Set<String> MUTABLE_TYPES = Set.of(
      "java.util.List", "java.util.Set", "java.util.Map", "java.util.Collection",
      "java.util.ArrayList", "java.util.HashMap", "java.util.HashSet",
      "java.util.Date", "java.util.Calendar");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.MODERN;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "Record component of a mutable type with no defensive copy";
  }

  @Override
  public String explanation() {
    return "Records imply immutability, but a List/Map/Date component stores the caller's "
        + "reference directly. The caller (or anyone the record is passed to) can mutate "
        + "that collection afterwards, changing the 'immutable' record's contents behind "
        + "the back of everything holding it.";
  }

  @Override
  public String fix() {
    return "Defensively copy in a compact constructor: MyRecord { items = List.copyOf("
        + "items); } — and return copies from accessors if callers might mutate the result.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitClass(ClassTree node, RuleContext ctx) {
        if (node.getKind() == Tree.Kind.RECORD
            && ctx.trees().getElement(getCurrentPath())
                instanceof javax.lang.model.element.TypeElement type) {
          String constructors = constructorText(node);
          for (javax.lang.model.element.RecordComponentElement component
              : type.getRecordComponents()) {
            if (MUTABLE_TYPES.contains(ctx.qualifiedNameOf(component.asType()))
                && !isDefensivelyCopied(constructors, component.getSimpleName().toString())) {
              ctx.report(node, "Record component '" + component.getSimpleName()
                  + "' is a mutable type stored by reference — callers can change it after "
                  + "construction. Defensively copy it (List.copyOf) in a compact "
                  + "constructor.");
            }
          }
        }
        return super.visitClass(node, ctx);
      }

      /** Concatenated source of all explicit constructor bodies (implicit ones are empty). */
      private String constructorText(ClassTree record) {
        StringBuilder sb = new StringBuilder();
        for (Tree member : record.getMembers()) {
          if (member instanceof com.sun.source.tree.MethodTree method
              && method.getName().contentEquals("<init>")
              && method.getBody() != null) {
            sb.append(method.getBody());
          }
        }
        return sb.toString();
      }

      /** A compact constructor that reassigns the component via a copying call defends it. */
      private boolean isDefensivelyCopied(String constructorText, String component) {
        return constructorText.contains(component + " = ")
            && (constructorText.contains("copyOf")
                || constructorText.contains("unmodifiable")
                || constructorText.contains(".clone()")
                || constructorText.contains("new ArrayList")
                || constructorText.contains("new HashMap")
                || constructorText.contains("new HashSet"));
      }
    };
  }
}
