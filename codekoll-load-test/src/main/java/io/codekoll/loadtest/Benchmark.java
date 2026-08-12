package io.codekoll.loadtest;

import io.codekoll.api.Rule;
import io.codekoll.engine.AnalysisResult;
import io.codekoll.engine.CompilationDriver;
import io.codekoll.engine.RuleRegistry;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs codekoll over a corpus with warm-up + measured iterations and reports the <em>fastest</em>
 * CPU time and the peak heap. CPU time comes from {@link com.sun.management.OperatingSystemMXBean}
 * (process CPU), which is far less noisy than wall time on shared CI runners.
 *
 * <p>Minimum rather than median: contention, GC and JIT compilation only ever <em>add</em> to a
 * measurement, so the fastest iteration is the one least contaminated by whatever else the runner
 * was doing. A median still carries that noise into the number the gate compares.
 */
final class Benchmark {

  private static final int WARMUP = 2;

  private final CompilationDriver driver = new CompilationDriver(25, "");
  private final List<Rule> rules = RuleRegistry.loadAll();

  Measurement measure(String tier, List<Path> corpus, int sourceLines, int iterations,
      long calibrationMillis, String env) {
    for (int i = 0; i < WARMUP; i++) {
      driver.analyzePaths(corpus, rules);
    }
    List<Long> wall = new ArrayList<>();
    List<Long> cpu = new ArrayList<>();
    long peakHeap = 0;
    long findings = 0;
    for (int i = 0; i < iterations; i++) {
      long startCpu = processCpuNanos();
      long startWall = System.nanoTime();
      AnalysisResult result = driver.analyzePaths(corpus, rules);
      long wallNanos = System.nanoTime() - startWall;
      long cpuNanos = processCpuNanos() - startCpu;
      findings = result.findings().size();
      wall.add(wallNanos / 1_000_000);
      cpu.add(cpuNanos / 1_000_000);
      peakHeap = Math.max(peakHeap, usedHeapAfterGc());
    }
    return new Measurement(tier, sourceLines, findings,
        fastest(wall), fastest(cpu), peakHeap, calibrationMillis, env);
  }

  private static long processCpuNanos() {
    java.lang.management.OperatingSystemMXBean os =
        ManagementFactory.getOperatingSystemMXBean();
    if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
      long v = sun.getProcessCpuTime();
      return v >= 0 ? v : System.nanoTime();
    }
    return System.nanoTime();
  }

  private static long usedHeapAfterGc() {
    System.gc();
    MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    return memory.getHeapMemoryUsage().getUsed();
  }

  private static long fastest(List<Long> values) {
    return values.stream().mapToLong(Long::longValue).min().orElse(0);
  }
}
