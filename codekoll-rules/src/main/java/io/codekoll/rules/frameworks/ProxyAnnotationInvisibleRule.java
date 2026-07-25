package io.codekoll.rules.frameworks;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.element.Modifier;

/**
 * CK-PROXY-ANNOTATION-INVISIBLE: {@code @Transactional}/{@code @Cacheable}/{@code @Async}/
 * {@code @Retryable}/{@code @Scheduled} on a private, final, or static method — Spring's
 * proxy cannot intercept it, so the annotation is silently ignored.
 */
public final class ProxyAnnotationInvisibleRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-PROXY-ANNOTATION-INVISIBLE");

  private static final Set<String> PROXY_ANNOTATIONS =
      Set.of("Transactional", "Cacheable", "CacheEvict", "Async", "Retryable", "Scheduled");

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
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "@Transactional/@Async/@Cacheable on a private, final, or static method";
  }

  @Override
  public String explanation() {
    return "Spring applies these annotations through a runtime proxy that overrides or "
        + "delegates the method — impossible for private, final and static methods. The "
        + "annotation is SILENTLY ignored: no transaction when the rollback is needed, no "
        + "cache, the 'async' method runs synchronously. Nothing throws; nothing logs.";
  }

  @Override
  public String fix() {
    return "Make the method public and non-final (and non-static), or move it to a "
        + "separate bean that is injected and called through its interface.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethod(MethodTree node, RuleContext ctx) {
        String annotation = proxyAnnotation(node);
        if (annotation != null) {
          Set<Modifier> flags = node.getModifiers().getFlags();
          String blocker = flags.contains(Modifier.PRIVATE) ? "private"
              : flags.contains(Modifier.STATIC) ? "static"
              : flags.contains(Modifier.FINAL) ? "final"
              : null;
          if (blocker != null) {
            ctx.report(node, "@" + annotation + " on a " + blocker + " method is silently "
                + "ignored — Spring's proxy cannot intercept it. Make it public and "
                + "non-final, or move it to a collaborator bean.");
          }
        }
        return super.visitMethod(node, ctx);
      }

      private String proxyAnnotation(MethodTree method) {
        for (AnnotationTree annotation : method.getModifiers().getAnnotations()) {
          String name = simpleName(annotation.getAnnotationType());
          if (PROXY_ANNOTATIONS.contains(name)) {
            return name;
          }
        }
        return null;
      }

      private String simpleName(Tree annotationType) {
        String text = annotationType.toString();
        int dot = text.lastIndexOf('.');
        return dot < 0 ? text : text.substring(dot + 1);
      }
    };
  }
}
