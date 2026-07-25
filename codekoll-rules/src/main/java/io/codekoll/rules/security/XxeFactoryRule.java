package io.codekoll.rules.security;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * CK-XXE-FACTORY: an XML parser factory created without any hardening call in the same
 * method — the default configuration is XXE-vulnerable.
 */
public final class XxeFactoryRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-XXE-FACTORY");

  private static final Set<String> FACTORY_TYPES = Set.of(
      "javax.xml.parsers.DocumentBuilderFactory",
      "javax.xml.parsers.SAXParserFactory",
      "javax.xml.stream.XMLInputFactory",
      "javax.xml.transform.TransformerFactory");

  private static final Set<String> HARDENING_CALLS = Set.of(
      "setFeature", "setProperty", "setExpandEntityReferences",
      "setXIncludeAware", "setValidating");

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
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "XML parser factory created without XXE hardening";
  }

  @Override
  public String explanation() {
    return "By default these factories resolve external entities and DOCTYPE declarations. "
        + "A hostile document can then read local files (<!ENTITY x SYSTEM "
        + "\"file:///etc/passwd\">), reach internal URLs (SSRF), or exhaust memory (billion "
        + "laughs). XML External Entity injection is a routine finding on any parser left "
        + "at defaults.";
  }

  @Override
  public String fix() {
    return "Disable DOCTYPE: factory.setFeature(\"http://apache.org/xml/features/"
        + "disallow-doctype-decl\", true); and disable external entities before parsing.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        if (node.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("newInstance")
            && isXmlFactory(select, ctx)
            && !enclosingMethodHardens()) {
          ctx.report(node, "This XML factory keeps XXE-vulnerable defaults. Call "
              + "setFeature(disallow-doctype-decl, true) before parsing.");
        }
        return super.visitMethodInvocation(node, ctx);
      }

      private boolean isXmlFactory(MemberSelectTree select, RuleContext ctx) {
        Element element = ctx.trees().getElement(
            new TreePath(getCurrentPath(), select.getExpression()));
        return element instanceof TypeElement type
            && FACTORY_TYPES.contains(type.getQualifiedName().toString());
      }

      /** Conservative: if the enclosing method hardens ANY factory, assume this one is safe. */
      private boolean enclosingMethodHardens() {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
          if (p.getLeaf() instanceof MethodTree method && method.getBody() != null) {
            Boolean found = method.getBody().accept(new TreeScanner<Boolean, Void>() {
              @Override
              public Boolean visitMethodInvocation(MethodInvocationTree call, Void unused) {
                if (call.getMethodSelect() instanceof MemberSelectTree sel
                    && HARDENING_CALLS.contains(sel.getIdentifier().toString())) {
                  return Boolean.TRUE;
                }
                return super.visitMethodInvocation(call, unused);
              }

              @Override
              public Boolean reduce(Boolean a, Boolean b) {
                return Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b);
              }
            }, null);
            return Boolean.TRUE.equals(found);
          }
        }
        return false;
      }
    };
  }
}
