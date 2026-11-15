package io.codekoll.rules.deadcode;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import io.codekoll.rules.support.NullFacts;
import java.util.HashSet;
import java.util.Set;
import javax.lang.model.element.Element;

/**
 * Every element referenced in a unit except as the target of a plain {@code =} assignment. In
 * {@code arr[i] = v} and {@code holder.f = v} the array, index and receiver are still reads.
 */
final class Reads {

  private Reads() {}

  static Set<Element> in(CompilationUnitTree unit, Trees trees) {
    Set<Element> reads = new HashSet<>();
    new TreePathScanner<Void, Void>() {
      @Override
      public Void visitIdentifier(IdentifierTree node, Void unused) {
        add(getCurrentPath());
        return super.visitIdentifier(node, unused);
      }

      @Override
      public Void visitMemberSelect(MemberSelectTree node, Void unused) {
        add(getCurrentPath());
        return super.visitMemberSelect(node, unused);
      }

      @Override
      public Void visitMemberReference(MemberReferenceTree node, Void unused) {
        add(getCurrentPath());
        return super.visitMemberReference(node, unused);
      }

      @Override
      public Void visitAssignment(AssignmentTree node, Void unused) {
        ExpressionTree target = NullFacts.unwrap(node.getVariable());
        if (target instanceof IdentifierTree) {
          scan(node.getExpression(), unused);
        } else if (target instanceof MemberSelectTree ms) {
          scan(ms.getExpression(), unused);
          scan(node.getExpression(), unused);
        } else {
          super.visitAssignment(node, unused);
        }
        return null;
      }

      private void add(TreePath path) {
        Element e = trees.getElement(path);
        if (e != null) {
          reads.add(e);
        }
      }
    }.scan(new TreePath(unit), null);
    return reads;
  }
}
