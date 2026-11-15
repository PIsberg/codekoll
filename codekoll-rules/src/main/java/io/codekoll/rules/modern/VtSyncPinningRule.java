package io.codekoll.rules.modern;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.BlockingCalls;
import io.codekoll.rules.support.RuleContext;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import org.jspecify.annotations.Nullable;

/**
 * CK-VT-SYNC-PINNING: a blocking call while holding a monitor, in a project that targets Java
 * 21-23, where a virtual thread blocked inside {@code synchronized} pins its carrier (fixed in JDK
 * 24 by JEP 491).
 */
public final class VtSyncPinningRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-VT-SYNC-PINNING");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.MODERN;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "Blocking while holding a monitor pins virtual threads before Java 24";
  }

  @Override
  public String explanation() {
    return "On JDK 21-23 a virtual thread that blocks inside a synchronized block or method "
        + "cannot unmount: it pins the carrier (platform) thread for the whole wait. There are "
        + "only as many carriers as cores, so a handful of threads waiting under a monitor stops "
        + "every other virtual thread in the JVM, and if the thread holding the lock needs a "
        + "carrier to release it, the application deadlocks. JDK 24 (JEP 491) removed the "
        + "limitation; this project's release still supports JDKs that have it.";
  }

  @Override
  public String fix() {
    return "Guard the blocking section with a java.util.concurrent.locks.ReentrantLock instead "
        + "of synchronized, move the blocking call out of the critical section, or raise the "
        + "project's minimum to Java 24.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      private final Set<Tree> reported = Collections.newSetFromMap(new IdentityHashMap<>());
      private @Nullable Boolean pinningPlatform;

      @Override
      public @Nullable Void visitSynchronized(SynchronizedTree node, RuleContext ctx) {
        if (pins(ctx)) {
          report(new TreePath(getCurrentPath(), node.getBlock()), ctx);
        }
        return super.visitSynchronized(node, ctx);
      }

      @Override
      public @Nullable Void visitMethod(MethodTree node, RuleContext ctx) {
        if (node.getBody() != null
            && node.getModifiers().getFlags().contains(Modifier.SYNCHRONIZED)
            && pins(ctx)) {
          report(new TreePath(getCurrentPath(), node.getBody()), ctx);
        }
        return super.visitMethod(node, ctx);
      }

      private void report(TreePath region, RuleContext ctx) {
        for (TreePath call : BlockingCalls.declaring(region, ctx,
            Set.of(BlockingCalls.INTERRUPTED, BlockingCalls.IO))) {
          if (reported.add(call.getLeaf())) {
            ctx.report(call.getLeaf(), "Blocking while holding a monitor pins a virtual thread's "
                + "carrier on Java 21-23, which this project still targets. Use a ReentrantLock "
                + "here, or block outside the synchronized region.");
          }
        }
      }

      private boolean pins(RuleContext ctx) {
        Boolean cached = pinningPlatform;
        if (cached == null) {
          cached = hasStaticMethod(ctx.elements(), "java.lang.Thread", "ofVirtual")
              && !hasStaticMethod(ctx.elements(), "java.io.Reader", "of");
          pinningPlatform = cached;
        }
        return cached;
      }
    };
  }

  /**
   * javac resolves platform classes for the {@code --release} it compiles against, so an API's
   * presence tells the release: {@code Thread.ofVirtual} arrived in 21 (preview in 19-20) and
   * {@code Reader.of(CharSequence)} in 24, the release JEP 491 shipped in.
   */
  private static boolean hasStaticMethod(Elements elements, String type, String method) {
    @Nullable TypeElement element = elements.getTypeElement(type);
    return element != null && ElementFilter.methodsIn(element.getEnclosedElements()).stream()
        .anyMatch(m -> m.getSimpleName().contentEquals(method)
            && m.getModifiers().contains(Modifier.STATIC));
  }
}
