package io.codekoll.api;

/** Sink for findings produced by a rule scan. Implementations apply suppression filtering. */
@FunctionalInterface
public interface FindingCollector {
  void report(Finding finding);
}
