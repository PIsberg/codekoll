package io.codekoll.rules.correctness;

import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * CK-SB-CHAR-CTOR: {@code new StringBuilder('a')} — the char widens to int and becomes the
 * <em>capacity</em>; nothing is appended.
 */
public final class SbCharCtorRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-SB-CHAR-CTOR");

  private static final Set<String> BUILDER_TYPES =
      Set.of("java.lang.StringBuilder", "java.lang.StringBuffer");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.CORRECTNESS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "new StringBuilder(char) sets capacity, not content";
  }

  @Override
  public String explanation() {
    return "StringBuilder has no char constructor, so the char silently widens to int and "
        + "selects the CAPACITY constructor: new StringBuilder('a') is an empty builder "
        + "with capacity 97. The character never appears in the output.";
  }

  @Override
  public String fix() {
    return "Use a string literal — new StringBuilder(\"a\") — or append the char: "
        + "new StringBuilder().append('a').";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitNewClass(NewClassTree node, RuleContext ctx) {
        if (node.getArguments().size() == 1
            && node.getArguments().get(0).getKind() == Tree.Kind.CHAR_LITERAL
            && node.getArguments().get(0) instanceof LiteralTree literal) {
          TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), node));
          if (BUILDER_TYPES.contains(ctx.qualifiedNameOf(type))) {
            ctx.report(node, "The char widens to int and becomes the CAPACITY ("
                + (int) ((Character) literal.getValue()).charValue()
                + "); nothing is appended. Use a string literal or .append(...).");
          }
        }
        return super.visitNewClass(node, ctx);
      }
    };
  }
}
