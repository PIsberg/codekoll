package io.codekoll.rules.support;

import com.sun.source.tree.CompilationUnitTree;
import java.net.URI;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Tells test sources from production sources by path. Rules receive no test-root signal from
 * workspace discovery, so this follows the Maven and Gradle source-set conventions instead.
 */
public final class SourceKinds {

  private static final Set<String> TEST_SOURCE_SETS =
      Set.of("test", "tests", "testFixtures", "integrationTest", "it");

  private SourceKinds() {}

  /** True when the unit lives under {@code src/<test source set>/}. */
  public static boolean isTestSource(CompilationUnitTree unit) {
    return isTestSource(unit.getSourceFile().toUri());
  }

  /**
   * Whether to treat this unit as a test. Trusts workspace discovery when it classified the unit,
   * and falls back to the directory convention only when it could not (fixtures, loose paths).
   */
  public static boolean isTestSource(RuleContext ctx) {
    return switch (ctx.sourceKind()) {
      case TEST -> true;
      case MAIN -> false;
      case UNKNOWN -> isTestSource(ctx.unit());
    };
  }

  /** True when {@code uri} has a {@code src} directory directly followed by a test source set. */
  public static boolean isTestSource(URI uri) {
    @Nullable String path = uri.getPath();
    if (path == null) {
      return false;
    }
    String[] segments = path.replace('\\', '/').split("/");
    for (int i = 0; i + 1 < segments.length; i++) {
      if ("src".equals(segments[i]) && TEST_SOURCE_SETS.contains(segments[i + 1])) {
        return true;
      }
    }
    return false;
  }
}
