package io.codekoll.rules.support;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import io.codekoll.api.FindingCollector;
import io.codekoll.api.Rule;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Base class for all built-in rules: wires the per-unit {@link RuleContext} and runs the
 * rule's scanner over the compilation unit.
 */
public abstract class AbstractRule implements Rule {

  @Override
  public final void scan(CompilationUnitTree unit, Trees trees, Types types, Elements elements,
      FindingCollector out) {
    RuleContext ctx = new RuleContext(this, unit, trees, types, elements, out);
    TreePathScanner<Void, RuleContext> scanner = scanner();
    scanner.scan(unit, ctx);
  }

  /** The AST scanner implementing this rule's detection. Receives the context as parameter. */
  protected abstract TreePathScanner<Void, RuleContext> scanner();
}
