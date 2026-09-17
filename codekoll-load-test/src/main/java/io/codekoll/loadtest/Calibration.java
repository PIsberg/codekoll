package io.codekoll.loadtest;

import java.lang.management.ManagementFactory;

/**
 * Measures how fast the machine running the benchmark is, independently of codekoll. Recorded
 * alongside every measurement as context; <em>not</em> used to normalize the gate.
 *
 * <p>It was, briefly, and CI disproved it. The idea was to divide codekoll's CPU time by this
 * fixed workload's, so that a slower machine would inflate both and cancel out. Measured:
 *
 * <pre>
 *   developer machine   calibration 109-125 ms   100k corpus 10 515 ms   ratio 96.5
 *   GitHub CI runner    calibration 150 ms       100k corpus  6 430 ms   ratio 42.9
 * </pre>
 *
 * <p>The two move in opposite directions. This loop is pure integer arithmetic in registers;
 * codekoll's work is allocation-, cache- and JIT-heavy. A machine can be worse at one and better
 * at the other, and these two were. A divisor that is anti-correlated with the numerator is worse
 * than no divisor, so the gate compares against a baseline recorded on the same kind of machine
 * instead (see {@link Environment}).
 *
 * <p>The number stays because it is useful context in {@code results/latest.json} when a run looks
 * odd: it separates "this machine is slow today" from "codekoll got slower".
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
