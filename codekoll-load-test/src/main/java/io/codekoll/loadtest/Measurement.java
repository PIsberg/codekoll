package io.codekoll.loadtest;

/**
 * One measured analysis run: throughput and resource cost of analyzing a corpus.
 *
 * @param tier corpus tier label ("small", "100k", "500k")
 * @param sourceLines total source lines analyzed
 * @param findings number of findings produced (sanity: two versions must do the same work)
 * @param wallMillis fastest wall-clock time of the measured iterations
 * @param cpuMillis fastest process CPU time (the gated metric — wall time is noisier still)
 * @param peakHeapBytes peak used heap after a forced GC
 * @param calibrationMillis CPU time of {@link Calibration}'s fixed workload on this machine, the
 *     divisor that makes the gate a ratio rather than a stopwatch reading
 */
record Measurement(
    String tier,
    int sourceLines,
    long findings,
    long wallMillis,
    long cpuMillis,
    long peakHeapBytes,
    long calibrationMillis) {

  double throughputKlocPerSec() {
    return cpuMillis == 0 ? 0 : (sourceLines / 1000.0) / (cpuMillis / 1000.0);
  }

  /** CPU cost expressed in units of the calibration workload: comparable across machines. */
  double calibratedCost() {
    return calibrationMillis <= 0 ? cpuMillis : cpuMillis / (double) calibrationMillis;
  }

  String toJson() {
    return String.format(
        "{\"tier\":\"%s\",\"sourceLines\":%d,\"findings\":%d,\"wallMillis\":%d,"
            + "\"cpuMillis\":%d,\"peakHeapBytes\":%d,\"calibrationMillis\":%d}",
        tier, sourceLines, findings, wallMillis, cpuMillis, peakHeapBytes, calibrationMillis);
  }
}
