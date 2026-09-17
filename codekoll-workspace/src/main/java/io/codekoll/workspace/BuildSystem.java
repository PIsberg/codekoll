package io.codekoll.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * How the target repository declares its build. Detected textually — codekoll reads build files,
 * it never evaluates them (CLI-SPEC §3.2).
 */
public enum BuildSystem {
  /** {@code pom.xml} present. */
  MAVEN("Maven"),
  /** {@code build.gradle[.kts]} or {@code settings.gradle[.kts]} present. */
  GRADLE("Gradle"),
  /** No build file, but the conventional {@code src/main/java} layout is used. */
  CONVENTIONAL("conventional layout"),
  /** Neither: a plain tree of {@code .java} files. */
  PLAIN("plain source tree");

  private static final List<String> GRADLE_FILES = List.of(
      "settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts");

  private final String label;

  BuildSystem(String label) {
    this.label = label;
  }

  /** Human-readable name for {@code --print-workspace} and verbose output. */
  public String label() {
    return label;
  }

  /**
   * Detects the build system declared in {@code dir} itself (not its ancestors).
   *
   * <p>A directory carrying both a {@code pom.xml} and Gradle files — which happens in
   * repositories that publish through both — is reported as {@link #MAVEN}, because the pom is
   * the one codekoll can read reliably enough to derive modules and language level from.
   *
   * @param dir directory to inspect
   * @return the detected build system, or {@code null} if the directory declares none
   */
  public static @Nullable BuildSystem detectIn(Path dir) {
    if (Files.isRegularFile(dir.resolve("pom.xml"))) {
      return MAVEN;
    }
    for (String name : GRADLE_FILES) {
      if (Files.isRegularFile(dir.resolve(name))) {
        return GRADLE;
      }
    }
    return null;
  }
}
