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
 * @param calibrationMillis CPU time of {@link Calibration}'s fixed workload on this machine —
 *     context for reading an odd run, not something the gate divides by
 * @param env the kind of machine this was measured on ({@link Environment}); a measurement is only
 *     ever compared against a baseline carrying the same value
 */
record Measurement(
    String tier,
    int sourceLines,
    long findings,
    long wallMillis,
    long cpuMillis,
    long peakHeapBytes,
    long calibrationMillis,
    String env) {

  double throughputKlocPerSec() {
    return cpuMillis == 0 ? 0 : (sourceLines / 1000.0) / (cpuMillis / 1000.0);
  }

  String toJson() {
    return String.format(
        "{\"env\":\"%s\",\"tier\":\"%s\",\"sourceLines\":%d,\"findings\":%d,"
            + "\"wallMillis\":%d,\"cpuMillis\":%d,\"peakHeapBytes\":%d,"
            + "\"calibrationMillis\":%d}",
        env, tier, sourceLines, findings, wallMillis, cpuMillis, peakHeapBytes,
        calibrationMillis);
  }
}
