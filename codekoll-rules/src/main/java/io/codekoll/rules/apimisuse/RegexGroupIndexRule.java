package io.codekoll.rules.apimisuse;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import org.jspecify.annotations.Nullable;

/**
 * CK-REGEX-GROUP-INDEX: a capturing group that the pattern does not have — {@code group(3)} on
 * a two-group regex, or a {@code $3} replacement reference. Both throw
 * {@code IndexOutOfBoundsException} at runtime.
 */
public final class RegexGroupIndexRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-REGEX-GROUP-INDEX");

  private static final Set<String> REPLACE_METHODS = Set.of("replaceAll", "replaceFirst");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.API_MISUSE;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.ERROR;
  }

  @Override
  public String description() {
    return "Capturing group referenced that the regex does not have";
  }

  @Override
  public String explanation() {
    return "Group numbers are not checked by the compiler: matcher.group(3) against a pattern "
        + "with two capturing groups, or a \"$3\" in a replacement string, throws "
        + "IndexOutOfBoundsException: No group 3 the first time that line runs. The count is "
        + "easy to get wrong because non-capturing groups (?:...) and lookarounds (?=...) look "
        + "like groups but are not numbered, so adding one to a pattern silently renumbers "
        + "every reference after it.";
  }

  @Override
  public String fix() {
    return "Count only the capturing groups — ( ... ) and (?<name>...) — and renumber, or use "
        + "named groups: (?<year>\\\\d{4}) with matcher.group(\"year\"), which fails loudly at "
        + "compile-of-pattern time instead of silently shifting.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      /** Pattern variables → capturing groups in their compile-time-constant regex. */
      private final Map<Element, Integer> patternGroups = new HashMap<>();
      /** Matcher variables → capturing groups of the pattern they came from. */
      private final Map<Element, Integer> matcherGroups = new HashMap<>();

      @Override
      public Void visitVariable(VariableTree node, RuleContext ctx) {
        Element declared = ctx.trees().getElement(getCurrentPath());
        ExpressionTree init = node.getInitializer();
        if (declared != null && init instanceof MethodInvocationTree call) {
          Integer groups = groupsOfPatternExpression(call, ctx);
          if (groups != null) {
            patternGroups.put(declared, groups);
          }
          Integer matcherOf = groupsOfMatcherExpression(call, ctx);
          if (matcherOf != null) {
            matcherGroups.put(declared, matcherOf);
          }
        }
        return super.visitVariable(node, ctx);
      }

      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, RuleContext ctx) {
        checkGroupCall(node, ctx);
        checkReplacement(node, ctx);
        return super.visitMethodInvocation(node, ctx);
      }

      /** {@code matcher.group(n)} where n exceeds the pattern's capturing-group count. */
      private void checkGroupCall(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() != 1
            || !(node.getMethodSelect() instanceof MemberSelectTree select)
            || !"group".equals(select.getIdentifier().toString())) {
          return;
        }
        Element receiver =
            ctx.trees().getElement(new TreePath(getCurrentPath(), select.getExpression()));
        Integer groups = receiver == null ? null : matcherGroups.get(receiver);
        Integer requested = intConstant(node.getArguments().get(0));
        if (groups != null && requested != null && requested > groups) {
          ctx.report(node, "This matcher's pattern has " + groups + " capturing group"
              + (groups == 1 ? "" : "s") + ", so group(" + requested + ") throws "
              + "IndexOutOfBoundsException: No group " + requested + ". Non-capturing groups "
              + "(?:...) and lookarounds are not numbered — recount, or use named groups.");
        }
      }

      /** {@code s.replaceAll(constantRegex, "…$n…")} where n exceeds the group count. */
      private void checkReplacement(MethodInvocationTree node, RuleContext ctx) {
        if (node.getArguments().size() != 2
            || !(node.getMethodSelect() instanceof MemberSelectTree select)
            || !REPLACE_METHODS.contains(select.getIdentifier().toString())) {
          return;
        }
        String regex = stringConstant(node.getArguments().get(0));
        String replacement = stringConstant(node.getArguments().get(1));
        if (regex == null || replacement == null) {
          return;
        }
        int groups = capturingGroupCount(regex);
        int referenced = firstOutOfRangeReference(replacement, groups);
        if (referenced > 0) {
          ctx.report(node, "The regex has " + groups + " capturing group"
              + (groups == 1 ? "" : "s") + ", so the replacement's $" + referenced + " throws "
              + "IndexOutOfBoundsException: No group " + referenced + ". Renumber the "
              + "reference, or escape it as \\\\$ if a literal dollar sign was meant.");
        }
      }

      /** Capturing-group count when {@code call} is {@code Pattern.compile(literal)}. */
      private @Nullable Integer groupsOfPatternExpression(MethodInvocationTree call,
          RuleContext ctx) {
        if (!(call.getMethodSelect() instanceof MemberSelectTree select)
            || !"compile".equals(select.getIdentifier().toString())
            || !isType(select.getExpression(), "java.util.regex.Pattern", ctx)
            || call.getArguments().isEmpty()) {
          return null;
        }
        String regex = stringConstant(call.getArguments().get(0));
        return regex == null ? null : capturingGroupCount(regex);
      }

      /**
       * Capturing-group count when {@code call} is {@code x.matcher(...)} — either on a Pattern
       * variable already tracked, or directly on an inline {@code Pattern.compile(literal)}.
       */
      private @Nullable Integer groupsOfMatcherExpression(MethodInvocationTree call,
          RuleContext ctx) {
        if (!(call.getMethodSelect() instanceof MemberSelectTree select)
            || !"matcher".equals(select.getIdentifier().toString())) {
          return null;
        }
        ExpressionTree source = select.getExpression();
        if (source instanceof MethodInvocationTree inline) {
          return groupsOfPatternExpression(inline, ctx);
        }
        Element element = ctx.trees().getElement(new TreePath(getCurrentPath(), source));
        return element == null ? null : patternGroups.get(element);
      }

      private boolean isType(ExpressionTree expr, String fqn, RuleContext ctx) {
        Element element = ctx.trees().getElement(new TreePath(getCurrentPath(), expr));
        return element instanceof TypeElement type
            && fqn.equals(type.getQualifiedName().toString());
      }

      private @Nullable String stringConstant(ExpressionTree expr) {
        return expr instanceof LiteralTree literal && literal.getValue() instanceof String s
            ? s : null;
      }

      private @Nullable Integer intConstant(ExpressionTree expr) {
        return expr instanceof LiteralTree literal && literal.getValue() instanceof Integer i
            ? i : null;
      }
    };
  }

  /**
   * Capturing groups in {@code regex}: plain {@code (} and named {@code (?<name>}, ignoring
   * escapes, character classes, and the non-capturing {@code (?:} / {@code (?=} / {@code (?<=}
   * forms.
   */
  static int capturingGroupCount(String regex) {
    int count = 0;
    boolean inCharacterClass = false;
    int i = 0;
    while (i < regex.length()) {
      char c = regex.charAt(i);
      if (c == '\\') {
        i += 2;
        continue;
      }
      if (inCharacterClass) {
        inCharacterClass = c != ']';
      } else if (c == '[') {
        inCharacterClass = true;
      } else if (c == '(' && isCapturing(regex, i)) {
        count++;
      }
      i++;
    }
    return count;
  }

  /** A group is capturing unless it starts {@code (?}, except for the named form {@code (?<n>}. */
  private static boolean isCapturing(String regex, int open) {
    if (open + 1 >= regex.length() || regex.charAt(open + 1) != '?') {
      return true;
    }
    // (?<name>…) captures; (?<=…) and (?<!…) are lookbehind and do not.
    return open + 3 < regex.length() && regex.charAt(open + 2) == '<'
        && Character.isLetter(regex.charAt(open + 3));
  }

  /**
   * The first {@code $n} reference in {@code replacement} whose leading digit exceeds
   * {@code groups}, or 0 when every reference resolves. Matches {@code Matcher}'s own parsing:
   * it consumes digits only while they keep naming an existing group, so a bad reference is
   * exactly one whose first digit is already out of range.
   */
  static int firstOutOfRangeReference(String replacement, int groups) {
    int i = 0;
    while (i < replacement.length()) {
      char c = replacement.charAt(i);
      if (c == '\\') {
        i += 2;
        continue;
      }
      if (c == '$' && i + 1 < replacement.length()
          && Character.isDigit(replacement.charAt(i + 1))) {
        int first = replacement.charAt(i + 1) - '0';
        if (first > groups) {
          return first;
        }
      }
      i++;
    }
    return 0;
  }
}
