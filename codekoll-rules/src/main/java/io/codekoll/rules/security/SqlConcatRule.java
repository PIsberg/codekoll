package io.codekoll.rules.security;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import io.codekoll.rules.support.TaintShapes;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * CK-SQL-CONCAT: a JDBC execute/prepare call whose SQL argument is built by concatenating
 * literals with non-constant expressions — the SQL-injection shape.
 */
public final class SqlConcatRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-SQL-CONCAT");

  private static final Set<String> SINK_METHODS = Set.of(
      "execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "addBatch",
      "prepareStatement", "prepareCall", "nativeSQL");

  private static final Set<String> SINK_RECEIVERS =
      Set.of("java.sql.Statement", "java.sql.Connection");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.SECURITY;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "SQL built by concatenating variables into the statement";
  }

  @Override
  public String explanation() {
    return "Concatenating a variable into SQL makes the variable part of the STATEMENT: "
        + "a value of \"'; DROP TABLE users; --\" rewrites the query. This is SQL "
        + "injection — decades old, still the top web vulnerability class, and one "
        + "user-controlled string away in this code.";
  }

  @Override
  public String fix() {
    return "Use bind parameters: prepareStatement(\"... WHERE id = ?\") and setString(1, "
        + "value) — the value can then never become SQL.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (!node.getArguments().isEmpty()
            && node.getMethodSelect() instanceof MemberSelectTree select
            && SINK_METHODS.contains(select.getIdentifier().toString())
            && isJdbcReceiver(select, ctx)
            && TaintShapes.isLiteralPlusNonConstant(node.getArguments().get(0))) {
          ctx.report(node, "Variables concatenated into SQL become part of the statement — "
              + "SQL injection. Use bind parameters (?) instead.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isJdbcReceiver(MemberSelectTree select, RuleContext ctx) {
        TypeMirror receiver =
            ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
        return SINK_RECEIVERS.stream().anyMatch(fqn -> ctx.isSubtypeOf(receiver, fqn));
      }
    };
  }
}
