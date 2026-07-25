package io.codekoll.rules.modern;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * CK-RECORD-ARRAY-COMPONENT: a record with an array component — the generated equals/
 * hashCode compare the array by reference, so equal contents are not equal records.
 */
public final class RecordArrayComponentRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-RECORD-ARRAY-COMPONENT");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Record component of array type breaks generated equals/hashCode";
  }

  @Override
  public String explanation() {
    return "Records generate equals/hashCode from their components — but arrays only have "
        + "identity equality. Two records holding different array objects with IDENTICAL "
        + "contents are NOT equal, hash differently, and toString prints "
        + "'[B@1a2b3c'. The value semantics the record promises silently do not hold.";
  }

  @Override
  public String fix() {
    return "Use List.copyOf(...) with a List component, or override equals/hashCode with "
        + "Arrays.equals/Arrays.hashCode explicitly.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitClass(ClassTree node, RuleContext ctx) {
        if (node.getKind() == Tree.Kind.RECORD) {
          for (Tree member : node.getMembers()) {
            // Record components surface as final instance fields in the record's members.
            if (member instanceof VariableTree component
                && isArrayTyped(component, ctx)
                && !hasExplicitEquals(node)) {
              ctx.report(component, "Array component '" + component.getName()
                  + "': generated equals/hashCode use array IDENTITY, not contents. "
                  + "Use a List, or override equals/hashCode with Arrays.equals.");
            }
          }
        }
        return super.visitClass(node, ctx);
      }

      private boolean isArrayTyped(VariableTree variable, RuleContext ctx) {
        TreePath typePath = ctx.trees().getPath(ctx.unit(), variable.getType());
        TypeMirror type = typePath == null ? null : ctx.typeOf(typePath);
        return type != null && type.getKind() == TypeKind.ARRAY;
      }

      private boolean hasExplicitEquals(ClassTree record) {
        return record.getMembers().stream()
            .anyMatch(m -> m instanceof com.sun.source.tree.MethodTree method
                && method.getName().contentEquals("equals")
                && method.getParameters().size() == 1
                && method.getBody() != null);
      }
    };
  }
}
