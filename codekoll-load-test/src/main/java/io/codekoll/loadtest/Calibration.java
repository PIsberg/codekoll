package io.codekoll.loadtest;

import java.lang.management.ManagementFactory;

/**
 * Measures how fast the machine running the benchmark is, independently of codekoll.
 *
 * <p>The regression gate used to compare raw milliseconds against a baseline recorded on whatever
 * machine happened to record it. That measures the runner as much as the analyzer: on GitHub's
 * shared runners the same unchanged code came in at +13.7 %, +15.8 % and +35.8 % against a
 * baseline from a developer machine, so which side of a 15 % budget a pull request landed on was
 * decided by who else was on the host.
 *
 * <p>So the gate compares a <em>ratio</em>: codekoll's CPU time divided by the CPU time of this
 * fixed workload on the same host, in the same JVM, moments earlier. A slower machine inflates
 * both and the ratio holds; a genuine regression inflates only the numerator and the ratio moves.
 *
 * <p>The workload is deliberately arithmetic — an integer mix with a data dependency between
 * iterations so nothing can be optimized away, no allocation, no I/O. It must not exercise
 * codekoll, or a real slowdown would scale the divisor too and hide itself.
 */
final class Calibration {

  /** Enough work to take a few hundred milliseconds on current hardware. */
  private static final int ROUNDS = 12;

  private static final int STEPS = 4_000_000;

  private static final int SAMPLES = 3;

  private Calibration() {}

  /**
   * @return CPU milliseconds for the fixed workload, the minimum of a few samples
   */
  static long cpuMillis() {
    // Warm-up: the first pass measures the JIT rather than the machine.
    spin();
    long best = Long.MAX_VALUE;
    for (int i = 0; i < SAMPLES; i++) {
      long start = processCpuNanos();
      long result = spin();
      long elapsed = processCpuNanos() - start;
      // Consume the result so the loop cannot be eliminated.
      if (result == Long.MIN_VALUE) {
        System.out.print("");
      }
      best = Math.min(best, elapsed / 1_000_000);
    }
    return Math.max(1, best);
  }

  private static long spin() {
    long acc = 0x9E3779B97F4A7C15L;
    for (int round = 0; round < ROUNDS; round++) {
      for (int i = 0; i < STEPS; i++) {
        acc += i;
        acc ^= acc >>> 29;
        acc *= 0xBF58476D1CE4E5B9L;
        acc ^= acc >>> 32;
      }
    }
    return acc;
  }

  private static long processCpuNanos() {
    java.lang.management.OperatingSystemMXBean os =
        ManagementFactory.getOperatingSystemMXBean();
    if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
      long value = sun.getProcessCpuTime();
      return value >= 0 ? value : System.nanoTime();
    }
    return System.nanoTime();
  }
}
