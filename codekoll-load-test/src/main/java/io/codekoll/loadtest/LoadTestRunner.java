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
    int iterations = full ? 7 : 5;

    // Measured before the analyzer so the machine's speed is captured on the same host, in the
    // same JVM, under whatever contention this run happens to face.
    String env = Environment.key();
    long calibration = Calibration.cpuMillis();
    System.out.println("environment: " + env + " (baselines are per-environment; performance "
        + "numbers do not travel between machines)");
    System.out.println("calibration: " + calibration + " ms for the fixed workload");

    Benchmark benchmark = new Benchmark();
    List<Measurement> measurements = new ArrayList<>();

    // Small checked-in corpus proxy: the codekoll-examples sources (varied, real).
    Path examples = root.resolve("codekoll-examples").resolve("src").resolve("main")
        .resolve("java");
    if (Files.isDirectory(examples)) {
      int lines = countLines(examples);
      measurements.add(benchmark.measure("small",
          collect(examples), lines, iterations, calibration, env));
    }

    // Generated tiers.
    List<Integer> tiers = full ? List.of(100_000, 500_000) : List.of(100_000);
    for (int targetLines : tiers) {
      Path dir = work.resolve("gen" + targetLines);
      List<Path> corpus = CorpusGenerator.generate(dir, targetLines);
      measurements.add(benchmark.measure(targetLines / 1000 + "k",
          corpus, targetLines, iterations, calibration, env));
    }

    print(measurements);
    writeResults(resultsDir.resolve("latest.json"), measurements);
    // Charts are cheap PNGs; regenerate on every run so docs/perf stays current.
    writeCharts(root.resolve("docs").resolve("perf"), measurements);

    int gate = checkRegression(moduleDir.resolve("baseline.json"), measurements,
        maxCpuRegression, maxHeapRegression, full);
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

  /**
   * Compares the largest generated tier against the baseline entry for this environment.
   *
   * @return 1 when the run regressed, or when no comparable baseline exists — a gate that cannot
   *     compare has not passed
   */
  private static int checkRegression(Path baselineFile, List<Measurement> measurements,
      double maxCpu, double maxHeap, boolean full) throws IOException {
    Measurement current = measurements.get(measurements.size() - 1);
    String env = current.env();

    if (Boolean.getBoolean("codekoll.loadtest.record")) {
      // Deliberate, opt-in, and never a side effect of an ordinary run: a baseline that rewrites
      // itself is a gate that agrees with whatever it just measured. Other environments' entries
      // are kept — recording on a laptop must not delete CI's numbers.
      String existing = Files.exists(baselineFile)
          ? Files.readString(baselineFile, StandardCharsets.UTF_8) : "";
      List<String> kept = objectsExcludingEnv(existing, env);
      List<String> merged = new ArrayList<>(kept);
      measurements.forEach(m -> merged.add(m.toJson()));
      Files.writeString(baselineFile,
          "[\n  " + String.join(",\n  ", merged) + "\n]\n");
      System.out.println("Recorded the '" + env + "' baseline (" + kept.size()
          + " entries from other environments kept).");
      return 0;
    }

    if (!Files.exists(baselineFile)) {
      System.err.println("REGRESSION GATE NOT RUN: no baseline.json. Record one deliberately"
          + " with -Dcodekoll.loadtest.record=true and commit it with the reason.");
      return 1;
    }

    String baseline = Files.readString(baselineFile, StandardCharsets.UTF_8);
    String entry = objectFor(baseline, env, current.tier());
    if (entry == null) {
      System.err.println("REGRESSION GATE NOT RUN: baseline has no '" + current.tier()
          + "' entry for environment '" + env + "'. Performance numbers do not travel between"
          + " machines, so this run has nothing it can honestly be compared against. Record one"
          + " on this machine with -Dcodekoll.loadtest.record=true, and commit it in its own"
          + " change with the reason.");
      return 1;
    }

    long baseCpu = extractLong(entry, "cpuMillis");
    long baseHeap = extractLong(entry, "peakHeapBytes");
    double cpuDelta = (current.cpuMillis() - baseCpu) / (double) baseCpu;
    double heapDelta = (current.peakHeapBytes() - baseHeap) / (double) baseHeap;
    double cpuBudget = cpuBudget(full, maxCpu);
    System.out.printf(Locale.ROOT,
        "vs baseline (%s, %s): CPU %+.1f%% (%d ms now, %d baseline, budget +%.0f%%), "
            + "heap %+.1f%%%n",
        env, current.tier(), cpuDelta * 100, current.cpuMillis(), baseCpu, cpuBudget * 100,
        heapDelta * 100);

    boolean fail = false;
    if (cpuDelta > cpuBudget) {
      System.err.printf(Locale.ROOT, "REGRESSION: CPU +%.1f%% exceeds +%.0f%% budget%n",
          cpuDelta * 100, cpuBudget * 100);
      fail = true;
    }
    if (heapDelta > maxHeap) {
      System.err.printf(Locale.ROOT, "REGRESSION: heap +%.1f%% exceeds +%.0f%% budget%n",
          heapDelta * 100, maxHeap * 100);
      fail = true;
    }
    return fail ? 1 : 0;
  }

  /**
   * The CPU budget: the configured one for the {@code full} profile, a gross-regression budget
   * for {@code quick}.
   *
   * <p>CPU time on a machine that is doing anything else is not measurable to ±15 %. Two CI runs
   * of an identical code path produced 6 430 ms and 3 010 ms for the 100k corpus, a factor of
   * 2.1, GitHub having handed out different hardware. The development machine gave 10 453 ms
   * idle and 17 031 ms with a build running alongside, a factor of 1.6. Both numbers are the
   * host, not the analyzer.
   *
   * <p>So {@code quick}, which runs on every push wherever a runner is free, checks for a
   * doubling rather than a creep. That is a weaker gate, and saying so is the honest alternative
   * to one that fails Markdown-only pull requests until everybody learns to ignore it. Two things
   * keep it worth running: heap holds its ±20 % budget, having moved 0.6 % between two CI runs
   * and 11.2 % across three, which is a fraction of CPU's 2.1x and follows the work more than the
   * host; and the findings count is recorded, so two versions doing different amounts of work
   * shows up whatever the timings say.
   *
   * <p>The tight CPU budget belongs where the machine is quiet and known. That is the nightly
   * {@code full} profile, which keeps {@code maxCpuRegression} as configured — on a dedicated
   * runner it means something, and CLI-PLAN Milestone 16 is where that runner gets set up.
   */
  private static double cpuBudget(boolean full, double configured) {
    return full ? configured : Math.max(configured, 1.0);
  }

  /** The baseline object for this environment and tier, or {@code null} if there is none. */
  private static String objectFor(String json, String env, String tier) {
    for (String object : objects(json)) {
      if (object.contains("\"env\":\"" + env + "\"") && object.contains("\"tier\":\"" + tier + "\"")) {
        return object;
      }
    }
    return null;
  }

  private static List<String> objectsExcludingEnv(String json, String env) {
    List<String> kept = new ArrayList<>();
    for (String object : objects(json)) {
      if (!object.contains("\"env\":\"" + env + "\"")) {
        kept.add(object);
      }
    }
    return kept;
  }

  /** Splits the top-level array into its objects; the file is written by {@link Measurement}. */
  private static List<String> objects(String json) {
    List<String> objects = new ArrayList<>();
    int open = json.indexOf('{');
    while (open >= 0) {
      int close = json.indexOf('}', open);
      if (close < 0) {
        break;
      }
      objects.add(json.substring(open, close + 1));
      open = json.indexOf('{', close + 1);
    }
    return objects;
  }

  /** Minimal JSON scrape for one numeric field of a single object. */
  private static long extractLong(String json, String field) {
    int fieldIdx = json.indexOf("\"" + field + "\":");
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
