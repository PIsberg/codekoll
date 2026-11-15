package io.codekoll.rules.support;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import java.util.List;

/** Control-flow helpers shared by rules that reason about statements that always exit. */
public final class Jumps {

  private Jumps() {}

  /**
   * True when the last statement of a non-empty list is a break, continue, return, throw or yield,
   * or a block that itself ends with one.
   */
  public static boolean endsWithJump(List<? extends StatementTree> statements) {
    Tree last = statements.get(statements.size() - 1);
    Tree.Kind kind = last.getKind();
    return kind == Tree.Kind.BREAK || kind == Tree.Kind.RETURN
        || kind == Tree.Kind.THROW || kind == Tree.Kind.CONTINUE
        || kind == Tree.Kind.YIELD
        || (last instanceof BlockTree block
            && !block.getStatements().isEmpty()
            && endsWithJump(block.getStatements()));
  }
}
