package io.codekoll.rules.support;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

/**
 * Type-relationship helpers shared by the generics-mismatch and equals-incompatible rules:
 * whether two reference types are provably unrelated (no subtype relation either way).
 */
public final class Types2 {

  private Types2() {}

  /**
   * True when both types are concrete declared reference types with no subtype relationship
   * in either direction — so a value of one can never be a value of the other. Object,
   * primitives, type variables, wildcards, and error types are never "provably unrelated"
   * (conservative: unknown → not flagged).
   */
  public static boolean provablyUnrelated(@Nullable TypeMirror a, @Nullable TypeMirror b,
      RuleContext ctx) {
    if (a == null || b == null
        || a.getKind() != TypeKind.DECLARED || b.getKind() != TypeKind.DECLARED) {
      return false;
    }
    String an = ctx.qualifiedNameOf(a);
    String bn = ctx.qualifiedNameOf(b);
    if (an.isEmpty() || bn.isEmpty()
        || "java.lang.Object".equals(an) || "java.lang.Object".equals(bn)
        || an.equals(bn)) {
      return false;
    }
    Types types = ctx.types();
    TypeMirror ea = types.erasure(a);
    TypeMirror eb = types.erasure(b);
    // Related if either is assignable to the other, or they share a non-Object relationship.
    return !types.isAssignable(ea, eb) && !types.isAssignable(eb, ea)
        && !types.isSubtype(ea, eb) && !types.isSubtype(eb, ea);
  }
}
