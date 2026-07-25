package io.codekoll.rules.numeric;

import com.sun.source.tree.LiteralTree;
import com.sun.source.util.TreePathScanner;
import io.codekoll.api.RuleId;
import io.codekoll.api.RulePack;
import io.codekoll.api.Severity;
import io.codekoll.rules.support.AbstractRule;
import io.codekoll.rules.support.RuleContext;
import com.sun.source.util.Trees;

/**
 * CK-OCTAL-LITERAL: a multi-digit int/long literal with a leading zero (not 0x/0b) — it is
 * OCTAL, so {@code 0100} is 64, not 100.
 */
public final class OctalLiteralRule extends AbstractRule {

  private static final RuleId ID = new RuleId("CK-OCTAL-LITERAL");

  @Override
  public RuleId id() {
    return ID;
  }

  @Override
  public RulePack pack() {
    return RulePack.NUMERIC;
  }

  @Override
  public Severity defaultSeverity() {
    return Severity.WARNING;
  }

  @Override
  public String description() {
    return "Integer literal with a leading zero is octal, not decimal";
  }

  @Override
  public String explanation() {
    return "A leading zero makes the literal OCTAL: int timeout = 0100 is 64, not 100. The "
        + "code reads as the decimal value the author intended, but computes a different "
        + "number — timeouts, ports and array sizes silently off by a base-8 factor.";
  }

  @Override
  public String fix() {
    return "Drop the leading zero for a decimal value; if octal (e.g. Unix permissions) is "
        + "intended, keep it and name the constant so.";
  }

  @Override
  protected TreePathScanner<Void, RuleContext> scanner() {
    return new TreePathScanner<>() {
      @Override
      public Void visitLiteral(LiteralTree node, RuleContext ctx) {
        if ((node.getValue() instanceof Integer || node.getValue() instanceof Long)
            && isOctalSource(node, ctx)) {
          long value = ((Number) node.getValue()).longValue();
          ctx.report(node, "Leading-zero literal is OCTAL: this is " + value
              + " in decimal, not what it reads as. Drop the leading zero.");
        }
        return super.visitLiteral(node, ctx);
      }

      /** The source text must start with 0, be multi-digit, and not be a hex/binary prefix. */
      private boolean isOctalSource(LiteralTree node, RuleContext ctx) {
        String source = sourceText(node, ctx);
        if (source == null || source.length() < 2 || source.charAt(0) != '0') {
          return false;
        }
        char second = Character.toLowerCase(source.charAt(1));
        if (second == 'x' || second == 'b' || second == '.' || second == 'l'
            || second == 'e') {
          return false;
        }
        // All remaining chars (minus a trailing L) must be octal digits.
        String digits = source.replaceAll("[lL_]", "");
        return digits.chars().allMatch(c -> c >= '0' && c <= '7') && digits.length() > 1;
      }

      private String sourceText(LiteralTree node, RuleContext ctx) {
        Trees trees = ctx.trees();
        long start = trees.getSourcePositions().getStartPosition(ctx.unit(), node);
        long end = trees.getSourcePositions().getEndPosition(ctx.unit(), node);
        if (start < 0 || end < 0) {
          return null;
        }
        try {
          CharSequence src = ctx.unit().getSourceFile().getCharContent(true);
          return src.subSequence((int) start, (int) end).toString();
        } catch (java.io.IOException e) {
          return null;
        }
      }
    };
  }
}
