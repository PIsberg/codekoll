package io.codekoll.rules.frameworks;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.HashSet;
import java.util.Set;

/**
 * CK-PROXY-SELF-INVOKE: a method calls another method of the SAME class that carries
 * {@code @Transactional}/{@code @Async}/{@code @Cacheable} via plain this-dispatch — the
 * call never crosses the proxy, so the annotation silently does not apply.
 */
public final class ProxySelfInvokeRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-PROXY-SELF-INVOKE");

  private static final Set<String> PROXY_ANNOTATIONS =
      Set.of("Transactional", "Cacheable", "CacheEvict", "Async", "Retryable");

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
    return "Self-invocation of a @Transactional/@Async method bypasses the proxy";
  }

  @Override
  public String explanation() {
    return "Spring's proxy wraps the BEAN, not this. When a method calls a sibling "
        + "@Transactional method through plain this.other(), the call goes straight to the "
        + "implementation and never touches the proxy — so no transaction, no cache, no "
        + "async on that path. It works when called from outside the bean and silently "
        + "doesn't when called internally.";
  }

  @Override
  public String fix() {
    return "Move the annotated method to a separate bean and inject it, or self-inject the "
        + "bean and call through the injected reference.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitClass(ClassTree node, RuleContext ctx) {
        Set<String> proxied = proxiedMethodNames(node);
        if (!proxied.isEmpty()) {
          node.accept(new com.sun.source.util.TreeScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
              String target = selfCallTarget(call);
              if (target != null && proxied.contains(target)) {
                ctx.report(call, "Self-invocation of proxied method '" + target
                    + "()' bypasses the Spring proxy — its annotation does not apply on "
                    + "this path. Call it through an injected bean.");
              }
              return super.visitMethodInvocation(call, unused);
            }
          }, null);
        }
        return super.visitClass(node, ctx);
      }

      private Set<String> proxiedMethodNames(ClassTree cls) {
        Set<String> names = new HashSet<>();
        for (Tree member : cls.getMembers()) {
          if (member instanceof MethodTree method && hasProxyAnnotation(method)) {
            names.add(method.getName().toString());
          }
        }
        return names;
      }

      private boolean hasProxyAnnotation(MethodTree method) {
        for (AnnotationTree annotation : method.getModifiers().getAnnotations()) {
          String text = annotation.getAnnotationType().toString();
          if (PROXY_ANNOTATIONS.contains(text.substring(text.lastIndexOf('.') + 1))) {
            return true;
          }
        }
        return false;
      }

      /** Returns the method name for a this-dispatched self-call, else null. */
      private String selfCallTarget(MethodInvocationTree call) {
        Tree select = call.getMethodSelect();
        if (select instanceof IdentifierTree id) {
          return id.getName().toString();  // unqualified → this.method()
        }
        if (select instanceof MemberSelectTree ms
            && ms.getExpression() instanceof IdentifierTree recv
            && "this".equals(recv.getName().toString())) {
          return ms.getIdentifier().toString();
        }
        return null;
      }
    };
  }
}
