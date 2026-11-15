package io.codekoll.rules.deadcode;

import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;

/** CK-UNREACHABLE: stub, not implemented yet. */
public final class UnreachableRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-UNREACHABLE");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.DEAD_CODE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "TODO";
  }

  @Override
  public String explanation() {
    return "TODO";
  }

  @Override
  public String fix() {
    return "TODO";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {};
  }
}
