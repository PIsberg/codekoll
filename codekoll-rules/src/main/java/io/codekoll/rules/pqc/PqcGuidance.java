package io.codekoll.rules.pqc;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import org.jspecify.annotations.Nullable;

/** Finding messages for the pqc rules, tailored to what the analyzed project's platform provides. */
final class PqcGuidance {

  private static final String KEM_CALL = "KEM.getInstance(\"ML-KEM\")";
  private static final String DSA_CALL = "Signature.getInstance(\"ML-DSA\")";
  private static final String NOT_BUILT_IN =
      "not built into this project's Java release: use Bouncy Castle PQC or target Java 24+";

  private PqcGuidance() {}

  /**
   * Whether the target platform has ML-KEM and ML-DSA built in. javac resolves platform classes for
   * the {@code --release} it compiles against, so the constants JDK 24 added to
   * {@code NamedParameterSpec} are visible exactly when the project targets release 24 or later.
   */
  static boolean builtInPqc(Elements elements) {
    @Nullable TypeElement spec = elements.getTypeElement("java.security.spec.NamedParameterSpec");
    if (spec == null) {
      return false;
    }
    Set<String> fields = ElementFilter.fieldsIn(spec.getEnclosedElements()).stream()
        .map(field -> field.getSimpleName().toString())
        .collect(Collectors.toSet());
    return fields.contains("ML_KEM_768") && fields.contains("ML_DSA_65");
  }

  static String keyEstablishment(List<String> names, boolean builtIn) {
    String written = String.join(", ", names);
    return written + " key establishment is quantum-vulnerable: traffic recorded today can be decrypted "
        + "later. Replace with ML-KEM (FIPS 203), " + availability(builtIn, KEM_CALL)
        + "; run it hybrid with " + written + " until peers migrate.";
  }

  static String signature(List<String> names, boolean builtIn) {
    return String.join(", ", names) + " signatures are quantum-vulnerable: a future quantum computer can "
        + "forge them. Replace with ML-DSA (FIPS 204), " + availability(builtIn, DSA_CALL) + ".";
  }

  static String keyMaterial(List<String> names, Role role, boolean builtIn) {
    String use;
    String replacement;
    String calls;
    switch (role) {
      case KEY_ESTABLISHMENT -> {
        use = "key establishment";
        replacement = "ML-KEM";
        calls = KEM_CALL;
      }
      case SIGNATURE -> {
        use = "signatures";
        replacement = "ML-DSA";
        calls = DSA_CALL;
      }
      default -> {
        use = "signatures or key establishment";
        replacement = "ML-KEM or ML-DSA";
        calls = KEM_CALL + ", " + DSA_CALL;
      }
    }
    return String.join(", ", names) + " key material is quantum-vulnerable (used for " + use + "). "
        + "Plan its replacement with " + replacement + ", " + availability(builtIn, calls) + ".";
  }

  private static String availability(boolean builtIn, String calls) {
    return builtIn ? "built into this project's Java platform (" + calls + ")" : NOT_BUILT_IN;
  }
}
