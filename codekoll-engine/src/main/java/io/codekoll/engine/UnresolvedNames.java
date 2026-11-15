package io.codekoll.engine;

import java.util.Locale;
import javax.tools.Diagnostic;
import org.jspecify.annotations.Nullable;

/**
 * Names the dependency a javac error says is missing, for the attribution summary (CLI-SPEC §6).
 *
 * <p>The public diagnostic API exposes a code and a message, not the arguments (those live on
 * {@code com.sun.tools.javac} internals, which codekoll never touches). So the code decides
 * whether an error is about a missing dependency, and the message, rendered in
 * {@link Locale#ROOT} by {@link CompilationDriver}, supplies the name. An error this class does
 * not recognize names nothing; the file still counts as unattributed.
 */
final class UnresolvedNames {

  private static final String PACKAGE_MISSING = "compiler.err.doesnt.exist";
  private static final String CLASS_FILE_MISSING = "compiler.err.cant.access";
  private static final String SYMBOL_MISSING = "compiler.err.cant.resolve.location";

  private UnresolvedNames() {
  }

  /**
   * The package or class the error says is missing, or {@code null} when it is not that kind of
   * error.
   *
   * <p>A "cannot find symbol" counts only when its location is a package
   * ({@code import java.util.NoSuchThing}). Located in a class, it is almost always the use of a
   * name whose import already failed, and counting it would list every missing class a second
   * time under its simple name.
   */
  static @Nullable String of(Diagnostic<?> diagnostic) {
    String code = diagnostic.getCode();
    if (code == null) {
      return null;
    }
    String message = diagnostic.getMessage(Locale.ROOT);
    return switch (code) {
      case PACKAGE_MISSING -> between(message, "package ", " does not exist");
      case CLASS_FILE_MISSING -> firstWord(after(message, "cannot access "));
      case SYMBOL_MISSING -> classInPackage(message);
      default -> null;
    };
  }

  private static @Nullable String classInPackage(String message) {
    String symbol = firstWord(after(message, "symbol: class "));
    String location = firstWord(after(message, "location: package "));
    return symbol == null || location == null ? null : location + "." + symbol;
  }

  private static @Nullable String between(String text, String prefix, String suffix) {
    String rest = after(text, prefix);
    if (rest == null) {
      return null;
    }
    int end = rest.indexOf(suffix);
    return end <= 0 ? null : rest.substring(0, end);
  }

  /** The text after {@code marker}, with runs of spaces in the message folded to one. */
  private static @Nullable String after(String text, String marker) {
    String folded = text.replaceAll(" +", " ");
    int at = folded.indexOf(marker);
    return at < 0 ? null : folded.substring(at + marker.length());
  }

  private static @Nullable String firstWord(@Nullable String text) {
    if (text == null) {
      return null;
    }
    String word = text.strip().split("\\s", 2)[0];
    return word.isEmpty() ? null : word;
  }
}
