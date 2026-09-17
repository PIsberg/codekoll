package io.codekoll.rules.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class SourceKindsTest {

  @Test
  void conventionalTestDirectoriesAreTestSources() {
    for (String uri : new String[] {
        "file:///r/src/test/java/A.java",
        "file:///r/mod/src/testFixtures/java/A.java",
        "file:///r/src/integrationTest/java/A.java",
        "file:///r/src/it/java/A.java",
        "string:///src/test/java/A.java",
        "file:///C:/r/src/test/java/A.java"}) {
      assertTrue(SourceKinds.isTestSource(URI.create(uri)), uri);
    }
  }

  @Test
  void mainSourcesAreNotTestSourcesEvenInAPackageNamedTest() {
    for (String uri : new String[] {
        "file:///r/src/main/java/test/A.java",
        "file:///r/src/main/java/A.java",
        "string:///A.java"}) {
      assertFalse(SourceKinds.isTestSource(URI.create(uri)), uri);
    }
  }
}
