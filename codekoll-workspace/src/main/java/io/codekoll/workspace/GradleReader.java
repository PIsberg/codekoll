package io.codekoll.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Reads what can honestly be read out of Gradle build scripts: declared subprojects and the Java
 * language level.
 *
 * <p>Gradle build scripts are programs, and codekoll only reads them as text. Everything here is
 * therefore best-effort by construction: what it cannot determine it reports as undetermined and
 * lets discovery fall back to the directory layout, which is never wrong, only less precise.
 * Deriving a language level from a script that computes it at configuration time is not
 * something a regex can do, and pretending otherwise would produce confidently wrong results.
 */
final class GradleReader {

  private static final List<String> SCRIPT_NAMES =
      List.of("build.gradle.kts", "build.gradle");
  private static final List<String> SETTINGS_NAMES =
      List.of("settings.gradle.kts", "settings.gradle");

  /** {@code sourceCompatibility = JavaVersion.VERSION_21} / {@code = 21} / {@code = "21"}. */
  private static final Pattern SOURCE_COMPATIBILITY = Pattern.compile(
      "(?:sourceCompatibility|targetCompatibility)\\s*(?:=|\\.set\\()\\s*"
          + "(?:JavaVersion\\.VERSION_)?[\"']?(\\d+)(?:_(\\d+))?[\"']?");

  /** {@code languageVersion = JavaLanguageVersion.of(21)}. */
  private static final Pattern TOOLCHAIN = Pattern.compile(
      "JavaLanguageVersion\\.of\\(\\s*[\"']?(\\d+)[\"']?\\s*\\)");

  /** {@code release = 21} / {@code release.set(21)}. */
  private static final Pattern RELEASE = Pattern.compile(
      "\\brelease\\s*(?:=|\\.set\\()\\s*[\"']?(\\d+)[\"']?");

  /** {@code include("a", ":b:c")} / {@code include ':a', ':b'}. */
  private static final Pattern INCLUDE = Pattern.compile("^\\s*include\\b(.*)$");
  private static final Pattern QUOTED = Pattern.compile("[\"']([^\"']+)[\"']");

  private GradleReader() {
  }

  /** Returns the language level declared in the directory's build script, or {@code null}. */
  static @Nullable String release(Path dir) {
    String script = readFirst(dir, SCRIPT_NAMES);
    if (script == null) {
      return null;
    }
    String stripped = stripComments(script);
    for (Pattern pattern : List.of(RELEASE, TOOLCHAIN, SOURCE_COMPATIBILITY)) {
      Matcher matcher = pattern.matcher(stripped);
      if (matcher.find()) {
        // JavaVersion.VERSION_1_8 captures as 1 then 8; everything modern captures a single group.
        String major = matcher.group(1);
        String minor = matcher.groupCount() >= 2 ? matcher.group(2) : null;
        return "1".equals(major) && minor != null ? minor : major;
      }
    }
    return null;
  }

  /**
   * Returns the subproject paths declared in the directory's settings script, as directory paths
   * relative to it ({@code :app:core} becomes {@code app/core}).
   */
  static List<String> subprojects(Path dir) {
    String settings = readFirst(dir, SETTINGS_NAMES);
    if (settings == null) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (String line : stripComments(settings).split("\\R")) {
      Matcher include = INCLUDE.matcher(line);
      if (!include.find()) {
        continue;
      }
      Matcher quoted = QUOTED.matcher(include.group(1));
      while (quoted.find()) {
        String name = quoted.group(1);
        while (name.startsWith(":")) {
          name = name.substring(1);
        }
        String asPath = name.replace(':', '/');
        if (!asPath.isEmpty()) {
          result.add(asPath);
        }
      }
    }
    return result;
  }

  /** True when the directory declares a Gradle build. */
  static boolean isGradleDir(Path dir) {
    return readFirst(dir, SCRIPT_NAMES) != null || readFirst(dir, SETTINGS_NAMES) != null;
  }

  private static @Nullable String readFirst(Path dir, List<String> names) {
    for (String name : names) {
      Path file = dir.resolve(name);
      if (Files.isRegularFile(file)) {
        try {
          return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
          // Unreadable build script: treat as absent. Discovery falls back to the layout.
          return null;
        }
      }
    }
    return null;
  }

  /** Removes {@code //} and {@code /* *}{@code /} comments so commented-out code is not read. */
  private static String stripComments(String script) {
    return script.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", "");
  }
}
