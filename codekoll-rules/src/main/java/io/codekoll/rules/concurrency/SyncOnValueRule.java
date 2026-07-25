package io.codekoll.rules.concurrency;

import com.sun.source.tree.SynchronizedTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.NullFacts;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * CK-SYNC-ON-VALUE: {@code synchronized} on a String or boxed primitive — these are
 * interned/cached and shared JVM-wide, so unrelated code may lock the same object.
 */
public final class SyncOnValueRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-SYNC-ON-VALUE");

  private static final Set<String> VALUE_TYPES = Set.of(
      "java.lang.String", "java.lang.Integer", "java.lang.Long", "java.lang.Double",
      "java.lang.Float", "java.lang.Short", "java.lang.Byte", "java.lang.Character",
      "java.lang.Boolean");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.CONCURRENCY;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "synchronized on a String or boxed primitive (shared/interned lock)";
  }

  @Override
  public String explanation() {
    return "String literals are interned and boxed primitives are cached (Integer -128..127, "
        + "all Booleans): the 'lock' object is shared JVM-wide. Completely unrelated code "
        + "synchronizing on the same value contends with — or deadlocks against — yours, "
        + "and your own lock may not be exclusive at all.";
  }

  @Override
  public String fix() {
    return "Lock on a dedicated object: private final Object lock = new Object(); — or use "
        + "java.util.concurrent locks.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitSynchronized(SynchronizedTree node, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(
            new TreePath(getCurrentPath(), NullFacts.unwrap(node.getExpression())));
        String name = ctx.qualifiedNameOf(type);
        if (VALUE_TYPES.contains(name)) {
          ctx.report(node, "synchronized on a " + name.replaceFirst(".*\\.", "")
              + ": interned/cached values are shared JVM-wide — the lock is not yours "
              + "alone. Use a private final Object lock.");
        }
        return super.visitSynchronized(node, ctx);
      }
    };
  }
}
