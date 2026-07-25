package io.codekoll.loadtest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministically generates a Java source corpus of a target size. Same tier → identical
 * output every run (no randomness, no timestamps), so measurements are comparable across
 * builds and machines. Files are seeded with a mix of clean code and codekoll-detectable
 * patterns so every pack's node kinds are exercised.
 */
final class CorpusGenerator {

  /** Approximate lines per generated class. */
  private static final int LINES_PER_CLASS = 40;

  private CorpusGenerator() {}

  /** Generates {@code targetLines} lines of Java across many files under {@code dir}. */
  static List<Path> generate(Path dir, int targetLines) throws java.io.IOException {
    Files.createDirectories(dir);
    List<Path> files = new ArrayList<>();
    int classes = Math.max(1, targetLines / LINES_PER_CLASS);
    for (int i = 0; i < classes; i++) {
      Path file = dir.resolve("Generated" + i + ".java");
      Files.writeString(file, classSource(i));
      files.add(file);
    }
    return files;
  }

  private static String classSource(int index) {
    // Rotate through a few shapes so the analyzer touches varied node kinds; all compile.
    int shape = index % 4;
    StringBuilder sb = new StringBuilder(1024);
    sb.append("package gen;\n\n");
    sb.append("import java.util.List;\n");
    sb.append("import java.util.Map;\n\n");
    sb.append("public class Generated").append(index).append(" {\n");
    sb.append("  private final String name = \"g").append(index).append("\";\n");
    for (int m = 0; m < 6; m++) {
      sb.append(methodSource(shape, m));
    }
    sb.append("}\n");
    return sb.toString();
  }

  private static String methodSource(int shape, int m) {
    return switch (shape) {
      case 0 -> """
              int compute%d(int a, int b) {
                int sum = 0;
                for (int i = 0; i < a; i++) {
                  sum += b * i;
                }
                return sum;
              }
          """.formatted(m);
      case 1 -> """
              String join%d(List<String> items) {
                StringBuilder sb = new StringBuilder();
                for (String item : items) {
                  sb.append(item).append(',');
                }
                return sb.toString();
              }
          """.formatted(m);
      case 2 -> """
              Object lookup%d(Map<String, Object> map, String key) {
                Object value = map.get(key);
                return value == null ? "default" : value;
              }
          """.formatted(m);
      default -> """
              boolean check%d(String s) {
                if (s == null || s.isEmpty()) {
                  return false;
                }
                return s.length() > 3;
              }
          """.formatted(m);
    };
  }
}
