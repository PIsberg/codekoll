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
import javax.lang.model.type.TypeMirror;

/**
 * CK-NATIVE-DESERIAL: {@code ObjectInputStream.readObject()} — native Java deserialization
 * is a well-known RCE vector when any input byte can be attacker-influenced.
 */
public final class NativeDeserialRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-NATIVE-DESERIAL");

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
    return Severity.INFO;
  }

  @Override
  public String description() {
    return "Native Java deserialization (readObject) — classic RCE vector";
  }

  @Override
  public String explanation() {
    return "ObjectInputStream.readObject() instantiates whatever classes the byte stream "
        + "names and runs their readObject methods — gadget chains in common libraries "
        + "turn that into arbitrary code execution. If any byte of the stream can be "
        + "influenced by an attacker, deserialization is remote code execution.";
  }

  @Override
  public String fix() {
    return "Prefer a data format (JSON/CBOR/protobuf). If native serialization must stay, "
        + "install a strict ObjectInputFilter allowlist before reading.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().isEmpty()
            && node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("readObject")) {
          TypeMirror receiver =
              ctx.typeOf(new TreePath(getCurrentPath(), select.getExpression()));
          if (ctx.isSubtypeOf(receiver, "java.io.ObjectInputStream")) {
            ctx.report(node, "readObject() on untrusted data is an RCE vector (gadget "
                + "chains). Use a data format, or install an ObjectInputFilter allowlist.");
          }
        }
        return super.visitMethodInvocation(node, ctx);
      }
    };
  }
}
