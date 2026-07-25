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
import javax.lang.model.type.TypeMirror;

/**
 * CK-MONITOR-ON-LOCK: {@code synchronized (lock)} where {@code lock} is a
 * {@code java.util.concurrent.locks.Lock} — the monitor and the Lock are independent
 * mechanisms; this provides zero exclusion against threads using {@code lock()}.
 */
public final class MonitorOnLockRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-MONITOR-ON-LOCK");

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
    return "synchronized on a java.util.concurrent Lock object";
  }

  @Override
  public String explanation() {
    return "A Lock's monitor and its lock()/unlock() state are entirely separate: "
        + "synchronized (reentrantLock) acquires the monitor, which threads calling "
        + "lock() never touch. The two groups of threads run 'mutually excluded' sections "
        + "concurrently — data races with a lock that looks correct in review.";
  }

  @Override
  public String fix() {
    return "Call lock.lock() in a try/finally with lock.unlock() — or use synchronized on a "
        + "plain Object consistently everywhere.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitSynchronized(SynchronizedTree node, RuleContext ctx) {
        TypeMirror type = ctx.typeOf(
            new TreePath(getCurrentPath(), NullFacts.unwrap(node.getExpression())));
        if (ctx.isSubtypeOf(type, "java.util.concurrent.locks.Lock")) {
          ctx.report(node, "synchronized on a Lock object uses the MONITOR, not the lock — "
              + "zero exclusion against threads calling lock(). "
              + "Use lock.lock() ... finally { lock.unlock(); }");
        }
        return super.visitSynchronized(node, ctx);
      }
    };
  }
}
