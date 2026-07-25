package io.codekoll.rules.performance;

import com.sun.source.tree.NewClassTree;
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
 * CK-NEW-WRAPPER: {@code new Integer(...)} & co (deprecated-for-removal boxing constructors)
 * and {@code new String(String)} — pure allocation waste that also defeats the cache.
 */
public final class NewWrapperRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-NEW-WRAPPER");

  private static final Set<String> WRAPPERS = Set.of(
      "java.lang.Integer", "java.lang.Long", "java.lang.Double", "java.lang.Float",
      "java.lang.Short", "java.lang.Byte", "java.lang.Character", "java.lang.Boolean");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.PERFORMANCE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Boxing constructor (new Integer) or new String(String)";
  }

  @Override
  public String explanation() {
    return "The wrapper constructors are deprecated for removal and always allocate — "
        + "bypassing the Integer/Boolean caches that valueOf uses — so hot paths churn "
        + "garbage for values the JVM would otherwise share. new String(String) copies a "
        + "string that is already immutable: allocation with zero benefit.";
  }

  @Override
  public String fix() {
    return "Use autoboxing or Integer.valueOf(...); use the String directly (or "
        + "substring/intern if a copy was genuinely intended).";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitNewClass(NewClassTree node, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(new TreePath(getCurrentPath(), node));
        String name = ctx.qualifiedNameOf(type);
        if (WRAPPERS.contains(name) && node.getArguments().size() == 1) {
          ctx.report(node, "new " + name.replaceFirst(".*\\.", "") + "(...) is deprecated "
              + "for removal and always allocates. Use valueOf or autoboxing.");
        } else if ("java.lang.String".equals(name) && node.getArguments().size() == 1) {
          TypeMirror argType = ctx.typeOf(
              new TreePath(getCurrentPath(), node.getArguments().get(0)));
          if ("java.lang.String".equals(ctx.qualifiedNameOf(argType))) {
            ctx.report(node, "new String(String) copies an immutable value — allocation "
                + "with zero benefit. Use the string directly.");
          }
        }
        return super.visitNewClass(node, ctx);
      }
    };
  }
}
