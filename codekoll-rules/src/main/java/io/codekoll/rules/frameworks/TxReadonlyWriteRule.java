package io.codekoll.rules.frameworks;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;
import org.jspecify.annotations.Nullable;

/**
 * CK-TX-READONLY-WRITE: a repository or entity-manager write inside a
 * {@code @Transactional(readOnly = true)} method, which is silently never flushed.
 */
public final class TxReadonlyWriteRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-TX-READONLY-WRITE");

  private static final Set<String> WRITES = Set.of("save", "saveAll", "saveAndFlush", "delete",
      "deleteAll", "deleteById", "deleteAllById", "persist", "merge", "remove");

  private static final List<String> PERSISTENCE_TYPES =
      List.of("Repository", "Dao", "EntityManager", "Session");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.FRAMEWORKS;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Write inside a @Transactional(readOnly = true) method";
  }

  @Override
  public String explanation() {
    return "Spring runs a readOnly transaction with Hibernate's flushing disabled and may mark "
        + "the JDBC connection read-only. A save, delete or @Modifying query inside it is either "
        + "never flushed (the change silently disappears at commit) or fails with a read-only "
        + "connection error, depending on the driver and database, so the same code can pass "
        + "against H2 in tests and lose data in production.";
  }

  @Override
  public String fix() {
    return "Remove readOnly = true from this method, or move the write into a read-write "
        + "@Transactional method.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public @Nullable Void visitMethod(MethodTree node, RuleContext ctx) {
        if (node.getBody() != null && readOnly(node, getCurrentPath())) {
          reportWrites(new TreePath(getCurrentPath(), node.getBody()), ctx);
        }
        return super.visitMethod(node, ctx);
      }
    };
  }

  /** The method's own @Transactional decides; without one, the enclosing class's. */
  private static boolean readOnly(MethodTree method, TreePath methodPath) {
    Optional<AnnotationTree> own = Annotations.find(method.getModifiers(), "Transactional");
    if (own.isPresent()) {
      return Annotations.booleanAttribute(own.get(), "readOnly").orElse(false);
    }
    TreePath parent = methodPath.getParentPath();
    return parent != null && parent.getLeaf() instanceof ClassTree owner
        && Annotations.find(owner.getModifiers(), "Transactional")
            .flatMap(annotation -> Annotations.booleanAttribute(annotation, "readOnly"))
            .orElse(false);
  }

  private static void reportWrites(TreePath body, RuleContext ctx) {
    new TreePathScanner<Void, Void>() {
      @Override
      public @Nullable Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        @Nullable Element callee = ctx.trees().getElement(getCurrentPath());
        boolean modifying = callee != null && Annotations.has(callee, "Modifying");
        if (modifying || isPersistenceWrite(node, getCurrentPath(), ctx)) {
          ctx.report(node, (modifying ? "This @Modifying query" : "This write")
              + " runs in a readOnly transaction: it is never flushed, or fails on a read-only "
              + "connection. Drop readOnly = true here, or write from a read-write transaction.");
        }
        return super.visitMethodInvocation(node, unused);
      }

      @Override
      public @Nullable Void visitClass(ClassTree node, Void unused) {
        return null;
      }
    }.scan(body, null);
  }

  private static boolean isPersistenceWrite(MethodInvocationTree node, TreePath path,
      RuleContext ctx) {
    if (!(node.getMethodSelect() instanceof MemberSelectTree select)
        || !WRITES.contains(select.getIdentifier().toString())) {
      return false;
    }
    String type = ctx.qualifiedNameOf(ctx.typeOf(new TreePath(path, select.getExpression())));
    String simple = type.substring(type.lastIndexOf('.') + 1);
    return PERSISTENCE_TYPES.stream().anyMatch(simple::endsWith);
  }
}
