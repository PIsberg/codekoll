package io.codekoll.rules.support;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import io.codekoll.api.Finding;
import io.codekoll.api.FindingCollector;
import io.codekoll.api.Rule;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

/** Per-compilation-unit context handed to rule scanners: type lookups + finding reporting. */
public final class RuleContext {

  private final Rule rule;
  private final CompilationUnitTree unit;
  private final Trees trees;
  private final Types types;
  private final Elements elements;
  private final FindingCollector out;
  private final SourcePositions positions;
  private final List<String> sourceLines;
  private final Path path;

  RuleContext(Rule rule, CompilationUnitTree unit, Trees trees, Types types, Elements elements,
      FindingCollector out) {
    this.rule = rule;
    this.unit = unit;
    this.trees = trees;
    this.types = types;
    this.elements = elements;
    this.out = out;
    this.positions = trees.getSourcePositions();
    this.sourceLines = readLines(unit);
    this.path = toPath(unit);
  }

  public CompilationUnitTree unit() {
    return unit;
  }

  public Trees trees() {
    return trees;
  }

  public Types types() {
    return types;
  }

  public Elements elements() {
    return elements;
  }

  /** Reports a finding anchored at {@code tree}, with the rule's default severity. */
  public void report(Tree tree, String message) {
    long pos = positions.getStartPosition(unit, tree);
    LineMap lineMap = unit.getLineMap();
    long line = pos >= 0 ? lineMap.getLineNumber(pos) : 0;
    long column = pos >= 0 ? lineMap.getColumnNumber(pos) : 0;
    String snippet = line >= 1 && line <= sourceLines.size()
        ? sourceLines.get((int) line - 1).strip()
        : "";
    out.report(new Finding(rule.id(), rule.defaultSeverity(), path, line, column, message,
        snippet));
  }

  /** Resolved type of the node at {@code path}, or null when attribution has no answer. */
  public @Nullable TypeMirror typeOf(TreePath treePath) {
    return trees.getTypeMirror(treePath);
  }

  /** True when {@code type} is the same as or a subtype of the class named {@code fqn}. */
  public boolean isSubtypeOf(@Nullable TypeMirror type, String fqn) {
    if (type == null) {
      return false;
    }
    TypeElement target = elements.getTypeElement(fqn);
    if (target == null) {
      return false;
    }
    return types.isSubtype(types.erasure(type), types.erasure(target.asType()));
  }

  /** Qualified name of the type element behind {@code type}, or "" when not a declared type. */
  public String qualifiedNameOf(@Nullable TypeMirror type) {
    if (type == null) {
      return "";
    }
    javax.lang.model.element.Element e = types.asElement(type);
    return e instanceof TypeElement te ? te.getQualifiedName().toString() : "";
  }

  private static List<String> readLines(CompilationUnitTree unit) {
    try {
      return unit.getSourceFile().getCharContent(true).toString().lines().toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private static Path toPath(CompilationUnitTree unit) {
    URI uri = unit.getSourceFile().toUri();
    if ("file".equals(uri.getScheme())) {
      return Path.of(uri);
    }
    String name = unit.getSourceFile().getName();
    return Path.of(name.startsWith("/") ? name.substring(1) : name);
  }
}
