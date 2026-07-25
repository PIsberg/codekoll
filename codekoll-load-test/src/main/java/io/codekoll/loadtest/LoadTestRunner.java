package io.codekoll.loadtest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Load-test entry point (invoked by the {@code quick}/{@code full} Maven profiles).
 *
 * <p>Generates the corpora, benchmarks the analyzer, prints results, and — crucially —
 * fails the build (exit 1) when CPU time or peak heap regresses beyond the thresholds versus
 * the committed {@code baseline.json}. The quick profile runs on every CI build; full also
 * regenerates the {@code docs/perf/} charts.
 *
 * <p>Args: {@code <quick|full> <maxCpuRegression> <maxHeapRegression>}.
 */
public final class LoadTestRunner {

  private LoadTestRunner() {}

  public static void main(String[] args) throws IOException {
    String profile = args.length > 0 ? args[0] : "quick";
    double maxCpuRegression = args.length > 1 ? Double.parseDouble(args[1]) : 0.15;
    double maxHeapRegression = args.length > 2 ? Double.parseDouble(args[2]) : 0.20;

    Path moduleDir = (args.length > 3 ? Path.of(args[3]) : Path.of("codekoll-load-test"))
        .toAbsolutePath();
    Path root = args.length > 4 ? Path.of(args[4]).toAbsolutePath() : moduleDir.getParent();
    Path work = moduleDir.resolve("target").resolve("corpus");
    Path resultsDir = moduleDir.resolve("results");
    Files.createDirectories(resultsDir);

    boolean full = "full".equals(profile);
    int iterations = full ? 5 : 3;

    Benchmark benchmark = new Benchmark();
    List<Measurement> measurements = new ArrayList<>();

    // Small checked-in corpus proxy: the codekoll-examples sources (varied, real).
    Path examples = root.resolve("codekoll-examples").resolve("src").resolve("main")
        .resolve("java");
    if (Files.isDirectory(examples)) {
      int lines = countLines(examples);
      measurements.add(benchmark.measure("small",
          collect(examples), lines, iterations));
    }

    // Generated tiers.
    List<Integer> tiers = full ? List.of(100_000, 500_000) : List.of(100_000);
    for (int targetLines : tiers) {
      Path dir = work.resolve("gen" + targetLines);
      List<Path> corpus = CorpusGenerator.generate(dir, targetLines);
      measurements.add(benchmark.measure(targetLines / 1000 + "k",
          corpus, targetLines, iterations));
    }

    print(measurements);
    writeResults(resultsDir.resolve("latest.json"), measurements);
    // Charts are cheap PNGs; regenerate on every run so docs/perf stays current.
    writeCharts(root.resolve("docs").resolve("perf"), measurements);

    int gate = checkRegression(moduleDir.resolve("baseline.json"), measurements,
        maxCpuRegression, maxHeapRegression);
    if (gate != 0) {
      System.exit(gate);
    }
  }

  private static void print(List<Measurement> measurements) {
    System.out.println();
    System.out.println("=== codekoll load test ===");
    System.out.printf("%-8s %10s %8s %10s %10s %12s%n",
        "tier", "lines", "findings", "cpu(ms)", "heap(MB)", "kLOC/s");
    for (Measurement m : measurements) {
      System.out.printf(Locale.ROOT, "%-8s %10d %8d %10d %10d %12.1f%n",
          m.tier(), m.sourceLines(), m.findings(), m.cpuMillis(),
          m.peakHeapBytes() / (1024 * 1024), m.throughputKlocPerSec());
    }
    System.out.println();
  }

  private static void writeResults(Path out, List<Measurement> measurements)
      throws IOException {
    StringBuilder sb = new StringBuilder("[\n");
    for (int i = 0; i < measurements.size(); i++) {
      sb.append("  ").append(measurements.get(i).toJson())
          .append(i < measurements.size() - 1 ? ",\n" : "\n");
    }
    sb.append("]\n");
    Files.writeString(out, sb.toString());
  }

  private static void writeCharts(Path dir, List<Measurement> measurements)
      throws IOException {
    Files.createDirectories(dir);
    List<String> labels = measurements.stream().map(Measurement::tier).toList();
    ChartRenderer.barChart(dir.resolve("cpu.png"), "CPU time by corpus", "ms",
        labels, measurements.stream().map(m -> (double) m.cpuMillis()).toList());
    ChartRenderer.barChart(dir.resolve("throughput.png"), "Throughput by corpus", "kLOC/s",
        labels, measurements.stream().map(Measurement::throughputKlocPerSec).toList());
    ChartRenderer.barChart(dir.resolve("heap.png"), "Peak heap by corpus", "MB",
        labels, measurements.stream()
            .map(m -> m.peakHeapBytes() / (1024.0 * 1024.0)).toList());
    System.out.println("Charts written to " + dir);
  }

  /** Compares the largest generated tier against baseline; returns 1 on regression. */
  private static int checkRegression(Path baselineFile, List<Measurement> measurements,
      double maxCpu, double maxHeap) throws IOException {
    if (!Files.exists(baselineFile)) {
      System.out.println("No baseline.json yet — recording current run as baseline v0.");
      writeResults(baselineFile, measurements);
      return 0;
    }
    String baseline = Files.readString(baselineFile, StandardCharsets.UTF_8);
    Measurement current = measurements.get(measurements.size() - 1);
    long baseCpu = extractLong(baseline, current.tier(), "cpuMillis");
    long baseHeap = extractLong(baseline, current.tier(), "peakHeapBytes");
    if (baseCpu <= 0) {
      System.out.println("Baseline has no '" + current.tier() + "' tier — skipping gate.");
      return 0;
    }
    double cpuDelta = (current.cpuMillis() - baseCpu) / (double) baseCpu;
    double heapDelta = (current.peakHeapBytes() - baseHeap) / (double) baseHeap;
    System.out.printf(Locale.ROOT, "vs baseline (%s): CPU %+.1f%%, heap %+.1f%%%n",
        current.tier(), cpuDelta * 100, heapDelta * 100);
    boolean fail = false;
    if (cpuDelta > maxCpu) {
      System.err.printf(Locale.ROOT, "REGRESSION: CPU +%.1f%% exceeds +%.0f%% budget%n",
          cpuDelta * 100, maxCpu * 100);
      fail = true;
    }
    if (heapDelta > maxHeap) {
      System.err.printf(Locale.ROOT, "REGRESSION: heap +%.1f%% exceeds +%.0f%% budget%n",
          heapDelta * 100, maxHeap * 100);
      fail = true;
    }
    return fail ? 1 : 0;
  }

  /** Minimal JSON scrape for one field of the object whose tier matches. */
  private static long extractLong(String json, String tier, String field) {
    int tierIdx = json.indexOf("\"tier\":\"" + tier + "\"");
    if (tierIdx < 0) {
      return -1;
    }
    int fieldIdx = json.indexOf("\"" + field + "\":", tierIdx);
    if (fieldIdx < 0) {
      return -1;
    }
    int start = fieldIdx + field.length() + 3;
    int end = start;
    while (end < json.length() && (Character.isDigit(json.charAt(end)))) {
      end++;
    }
    return end > start ? Long.parseLong(json.substring(start, end)) : -1;
  }

  private static List<Path> collect(Path dir) throws IOException {
    try (var walk = Files.walk(dir)) {
      return new ArrayList<>(walk.filter(p -> p.toString().endsWith(".java")).toList());
    }
  }

  private static int countLines(Path dir) throws IOException {
    int[] total = {0};
    try (var walk = Files.walk(dir)) {
      walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
        try {
          total[0] += Files.readAllLines(p).size();
        } catch (IOException ignored) {
          // best-effort line count
        }
      });
    }
    return total[0];
  }
}
