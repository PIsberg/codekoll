package io.codekoll.api;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * One static-analysis rule. Implementations are discovered via {@link java.util.ServiceLoader}
 * and must be stateless: {@link #scan} may be called for many compilation units.
 *
 * <p>{@link #description()}, {@link #explanation()} and {@link #fix()} are load-bearing
 * metadata: the README catalog, the examples READMEs, SARIF descriptors, {@code --explain}
 * and finding messages are generated from them. They must never be empty.
 */
public interface Rule {

  RuleId id();

  RulePack pack();

  Severity defaultSeverity();

  /** One line: what the rule looks for. */
  String description();

  /** What is wrong and what happens at runtime. */
  String explanation();

  /** How to fix it, one or two sentences. */
  String fix();

  /** Visit one attributed compilation unit; report findings via the collector. */
  void scan(CompilationUnitTree unit, Trees trees, Types types, Elements elements,
      FindingCollector out);
}
