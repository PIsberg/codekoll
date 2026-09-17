package io.codekoll.api;

/**
 * Whether a compilation unit is production code or a test, as workspace discovery decided from the
 * build's source sets. Rules that should stay quiet in tests, for example about throwaway keys or a
 * seeded {@code Random}, ask for this rather than guessing from the path.
 */
public enum SourceKind {
  /** Production code. */
  MAIN,
  /** A test source set of the analyzed build. */
  TEST,
  /**
   * The caller did not say: a fixture harness, or a run over loose paths rather than a discovered
   * workspace. A rule may fall back to a path convention, but must not treat this as {@link #MAIN}.
   */
  UNKNOWN
}
