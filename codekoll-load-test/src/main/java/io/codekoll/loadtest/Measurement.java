package io.codekoll.loadtest;

/**
 * One measured analysis run: throughput and resource cost of analyzing a corpus.
 *
 * @param tier corpus tier label ("small", "100k", "500k")
 * @param sourceLines total source lines analyzed
 * @param findings number of findings produced (sanity: two versions must do the same work)
 * @param wallMillis median wall-clock time
 * @param cpuMillis median process CPU time (the regression-gated metric — wall time is noisy)
 * @param peakHeapBytes peak used heap after a forced GC
 */
record Measurement(
    String tier,
    int sourceLines,
    long findings,
    long wallMillis,
    long cpuMillis,
    long peakHeapBytes) {

  double throughputKlocPerSec() {
    return cpuMillis == 0 ? 0 : (sourceLines / 1000.0) / (cpuMillis / 1000.0);
  }

  String toJson() {
    return String.format(
        "{\"tier\":\"%s\",\"sourceLines\":%d,\"findings\":%d,\"wallMillis\":%d,"
            + "\"cpuMillis\":%d,\"peakHeapBytes\":%d}",
        tier, sourceLines, findings, wallMillis, cpuMillis, peakHeapBytes);
  }
}
