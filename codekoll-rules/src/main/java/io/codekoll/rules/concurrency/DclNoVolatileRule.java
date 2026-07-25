package io.codekoll.rules.concurrency;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;

/**
 * CK-DCL-NO-VOLATILE: the double-checked-locking shape — outer if(f==null) / synchronized /
 * inner if(f==null) / f = ... — where field {@code f} is not volatile. Broken publication
 * under the JMM.
 */
public final class DclNoVolatileRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-DCL-NO-VOLATILE");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.CONCURRENCY;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Double-checked locking on a non-volatile field";
  }

  @Override
  public String explanation() {
    return "Without volatile, the JMM lets another thread see a NON-NULL reference to a "
        + "partially-constructed object: the field write can become visible before the "
        + "constructor's writes. The second thread skips the lock, uses the half-built "
        + "instance, and fails in ways that never reproduce under a debugger.";
  }

  @Override
  public String fix() {
    return "Declare the field volatile — or use a holder class (lazy static init) or an "
        + "enum singleton, which the JVM makes safe for free.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitIf(IfTree node, RuleContext ctx) {
        // Outer: if (f == null) { synchronized (...) { if (f == null) { f = ...; } } }
        String outerField = nullCheckedField(node.getCondition());
        if (outerField != null) {
          SynchronizedTree sync = singleSynchronized(node.getThenStatement());
          if (sync != null) {
            IfTree inner = singleIf(sync.getBlock());
            if (inner != null && outerField.equals(nullCheckedField(inner.getCondition()))
                && assignsField(inner.getThenStatement(), outerField)
                && isNonVolatileField(node.getCondition(), ctx)) {
              ctx.report(node, "Double-checked locking on non-volatile '" + outerField
                  + "' can publish a half-constructed object. Make the field volatile.");
            }
          }
        }
        return super.visitIf(node, ctx);
      }

      private @Nullable String nullCheckedField(com.sun.source.tree.ExpressionTree condition) {
        var fact = NullFacts.factOf(condition);
        return fact != null && fact.kind() == NullFacts.Kind.NULL ? fact.name() : null;
      }

      private @Nullable SynchronizedTree singleSynchronized(StatementTree statement) {
        StatementTree body = unwrapSingle(statement);
        return body instanceof SynchronizedTree sync ? sync : null;
      }

      private @Nullable IfTree singleIf(BlockTree block) {
        return block.getStatements().size() == 1
            && block.getStatements().get(0) instanceof IfTree ifTree ? ifTree : null;
      }

      private boolean assignsField(StatementTree statement, String field) {
        StatementTree body = unwrapSingle(statement);
        return body instanceof com.sun.source.tree.ExpressionStatementTree expr
            && expr.getExpression() instanceof AssignmentTree assign
            && fieldName(assign.getVariable()).equals(field);
      }

      private @Nullable StatementTree unwrapSingle(StatementTree statement) {
        if (statement instanceof BlockTree block && block.getStatements().size() == 1) {
          return block.getStatements().get(0);
        }
        return statement;
      }

      private String fieldName(com.sun.source.tree.ExpressionTree target) {
        String text = target.toString();
        return text.startsWith("this.") ? text.substring("this.".length()) : text;
      }

      private boolean isNonVolatileField(com.sun.source.tree.ExpressionTree condition,
          RuleContext ctx) {
        if (!(NullFacts.unwrap(condition) instanceof com.sun.source.tree.BinaryTree binary)) {
          return false;
        }
        for (com.sun.source.tree.ExpressionTree operand :
            new com.sun.source.tree.ExpressionTree[] {
                binary.getLeftOperand(), binary.getRightOperand()}) {
          if (NullFacts.unwrap(operand).getKind() == Tree.Kind.IDENTIFIER) {
            Element symbol = ctx.trees().getElement(
                new TreePath(getCurrentPath(), NullFacts.unwrap(operand)));
            if (symbol != null && symbol.getKind() == ElementKind.FIELD) {
              return !symbol.getModifiers().contains(Modifier.VOLATILE);
            }
          }
        }
        return false;
      }
    };
  }
}
