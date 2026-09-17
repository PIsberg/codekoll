package io.codekoll.rules.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.codekoll.engine.AnalysisResult;
import io.codekoll.engine.testing.RuleTestHarness;
import io.codekoll.rules.security.WeakCryptoRule;
import org.junit.jupiter.api.Test;

/** The harness must compile fixtures at the release a test asks for, not always at 25. */
class RuleTestHarnessReleaseTest {

  private static final String USES_ML_KEM_CONSTANT = """
      import java.security.spec.NamedParameterSpec;
      class R1 {
        Object spec = NamedParameterSpec.ML_KEM_768;
      }
      """;

  @Test
  void release21LacksPostQuantumParameterConstants() {
    AnalysisResult result = RuleTestHarness.run(new WeakCryptoRule(), "R1", USES_ML_KEM_CONSTANT, 21);
    assertEquals(1, result.skippedFiles().size(), "release 21 must not resolve ML_KEM_768");
  }

  @Test
  void release25ResolvesPostQuantumParameterConstants() {
    AnalysisResult result = RuleTestHarness.run(new WeakCryptoRule(), "R1", USES_ML_KEM_CONSTANT, 25);
    assertTrue(result.skippedFiles().isEmpty(), "release 25 must resolve ML_KEM_768");
  }

  @Test
  void defaultOverloadStillCompilesAtRelease25() {
    AnalysisResult result = RuleTestHarness.run(new WeakCryptoRule(), "R1", USES_ML_KEM_CONSTANT);
    assertTrue(result.skippedFiles().isEmpty(), "the three-argument run must keep release 25");
  }
}
