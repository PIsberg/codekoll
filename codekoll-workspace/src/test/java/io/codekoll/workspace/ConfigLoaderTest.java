package io.codekoll.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Configuration discovery, merging, provenance, and the §14 limits on what a repository may say
 * about the machine analyzing it.
 *
 * <p>The security half of this suite is the reason the layer exists. A `codekoll.toml` arrives
 * with the repository being analyzed — on a foreign repo, written by someone the user has no
 * reason to trust. It may say what to analyze. It may not say "and while you are here, run my
 * build" or "load these jars".
 */
class ConfigLoaderTest {

  @TempDir
  Path repo;

  @TempDir
  Path userHome;

  private final List<String> diagnostics = new ArrayList<>();

  private void write(Path dir, String relative, String content) throws IOException {
    Path file = dir.resolve(relative);
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }

  private Configuration load() {
    return ConfigLoader.load(repo, null, null, diagnostics);
  }

  private Configuration loadWithUserConfig() {
    return ConfigLoader.load(repo, null, userHome, diagnostics);
  }

  private String rejection(Runnable action) {
    return assertThrows(ConfigException.class, action::run).getMessage();
  }

  // ------------------------------------------------------------- discovery

  @Test
  void noConfigFileIsNormalAndSaidOutLoud() {
    Configuration config = load();

    assertTrue(config.isEmpty());
    assertTrue(String.join("\n", diagnostics).contains("no codekoll.toml"), diagnostics.toString());
  }

  @Test
  void theRepoConfigIsFoundUnderAnyOfItsThreeNames() throws IOException {
    write(repo, ".config/codekoll.toml", "[compile]\nrelease = 17\n");

    assertEquals(17, load().integer("compile.release").orElseThrow());
  }

  @Test
  void codekollTomlWinsOverItsDottedAndNestedSiblings() throws IOException {
    write(repo, "codekoll.toml", "[compile]\nrelease = 21\n");
    write(repo, ".codekoll.toml", "[compile]\nrelease = 17\n");

    assertEquals(21, load().integer("compile.release").orElseThrow());
  }

  @Test
  void theRepoConfigOverridesTheUserConfigKeyByKey() throws IOException {
    write(userHome, "codekoll/config.toml", """
        [compile]
        release = 11

        [report]
        format = "sarif"
        """);
    write(repo, "codekoll.toml", "[compile]\nrelease = 21\n");

    Configuration config = loadWithUserConfig();

    assertEquals(21, config.integer("compile.release").orElseThrow(), "repo wins");
    assertEquals("sarif", config.string("report.format").orElseThrow(), "user value survives");
  }

  @Test
  void anExplicitConfigReplacesTheRepoConfigRatherThanMergingWithIt() throws IOException {
    write(repo, "codekoll.toml", "[compile]\nrelease = 21\n");
    Path explicit = repo.resolve("elsewhere.toml");
    Files.writeString(explicit, "[report]\nformat = \"json\"\n");

    Configuration config = ConfigLoader.load(repo, explicit, null, diagnostics);

    assertTrue(config.integer("compile.release").isEmpty(), "the repo config is not consulted");
    assertEquals("json", config.string("report.format").orElseThrow());
  }

  @Test
  void aMissingExplicitConfigIsAnErrorNotASilentFallback() {
    assertTrue(rejection(() -> ConfigLoader.load(repo, repo.resolve("nope.toml"), null,
        diagnostics)).contains("does not exist"));
  }

  // ----------------------------------------------------------- provenance

  @Test
  void everyValueRemembersTheFileAndLineItCameFrom() throws IOException {
    write(userHome, "codekoll/config.toml", "[report]\nformat = \"sarif\"\n");
    write(repo, "codekoll.toml", """
        [compile]
        release = 21
        """);

    Configuration config = loadWithUserConfig();

    assertEquals("config.toml:2", config.originOf("report.format").orElseThrow());
    assertEquals("codekoll.toml:2", config.originOf("compile.release").orElseThrow());
  }

  // ------------------------------------------------------------- schema

  @Test
  void anUnknownKeyIsAnErrorThatSuggestsTheRealOne() throws IOException {
    write(repo, "codekoll.toml", "[rules]\ndisable-pack = [\"performance\"]\n");

    String message = rejection(this::load);

    assertTrue(message.contains("codekoll.toml:2"), message);
    assertTrue(message.contains("unknown key 'rules.disable-pack'"), message);
    assertTrue(message.contains("did you mean 'rules.disable-packs'"), message);
  }

  @Test
  void aKeyOutsideAnyTableIsAnError() throws IOException {
    write(repo, "codekoll.toml", "release = 21\n");

    assertTrue(rejection(this::load).contains("put 'release' under a table"));
  }

  @Test
  void theSeverityTableAcceptsAnyRuleIdAndLowercasesTheLevel() throws IOException {
    write(repo, "codekoll.toml", """
        [severity]
        "CK-THREAD-RUN" = "ERROR"
        "CK-EMPTY-CATCH" = "info"
        """);

    assertEquals(Map.of("CK-THREAD-RUN", "error", "CK-EMPTY-CATCH", "info"),
        load().table("severity"));
  }

  @Test
  void aValueOfTheWrongTypeNamesTheKeyAndTheOrigin() throws IOException {
    write(repo, "codekoll.toml", "[compile]\nrelease = \"21\"\n");

    String message = rejection(() -> load().integer("compile.release"));

    assertTrue(message.contains("compile.release"), message);
    assertTrue(message.contains("codekoll.toml:2"), message);
    assertTrue(message.contains("an integer"), message);
  }

  @Test
  void aSyntaxErrorIsReportedAsAConfigProblemWithItsLine() throws IOException {
    write(repo, "codekoll.toml", "[rules]\ndisable = [\"unterminated\n");

    String message = rejection(this::load);

    assertTrue(message.contains("repo config"), message);
    assertTrue(message.contains("codekoll.toml:2"), message);
  }

  // ------------------------------------------- §14: the repo is untrusted

  @Test
  void aRepoMayNotAskCodekollToRunItsBuild() throws IOException {
    write(repo, "codekoll.toml", "[resolve]\nmode = \"build\"\n");

    String message = rejection(this::load);

    assertTrue(message.contains("resolve.mode"), message);
    assertTrue(message.contains("may not be set by the analyzed repository"), message);
    assertTrue(message.contains("--resolve build yourself"), message);
  }

  @Test
  void aRepoMayNotAskCodekollToRunItsBuildUnderAnAlias() throws IOException {
    write(repo, "codekoll.toml", "[resolve]\nmode = \"AUTO\"\n");

    assertTrue(rejection(this::load).contains("resolve.mode"));
  }

  @Test
  void aRepoMayStillChooseTheHermeticModes() throws IOException {
    write(repo, "codekoll.toml", "[resolve]\nmode = \"none\"\n");

    assertEquals("none", load().string("resolve.mode").orElseThrow());
  }

  @Test
  void aRepoMayNotLoadRuleCodeItControls() throws IOException {
    write(repo, "codekoll.toml", "[rules]\nrule-path = [\"evil.jar\"]\n");

    String message = rejection(this::load);

    assertTrue(message.contains("rules.rule-path"), message);
    assertTrue(message.contains("loads code from a path the repository controls"), message);
  }

  @Test
  void aRepoMayNotWriteOutputOutsideItself() throws IOException {
    write(repo, "codekoll.toml", "[report]\noutput = \"../../etc/codekoll.sarif\"\n");

    String message = rejection(this::load);

    assertTrue(message.contains("report.output"), message);
    assertTrue(message.contains("outside the repository"), message);
  }

  @Test
  void aRepoMayWriteOutputInsideItself() throws IOException {
    write(repo, "codekoll.toml", "[report]\noutput = \"build/codekoll.sarif\"\n");

    assertEquals("build/codekoll.sarif", load().string("report.output").orElseThrow());
  }

  /** The same keys are legitimate from the user's own config, which the user wrote. */
  @Test
  void theUserConfigMaySetWhatTheRepoMayNot() throws IOException {
    write(userHome, "codekoll/config.toml", """
        [resolve]
        mode = "build"

        [rules]
        rule-path = ["/opt/codekoll/extra-rules.jar"]
        """);

    Configuration config = loadWithUserConfig();

    assertEquals("build", config.string("resolve.mode").orElseThrow());
    assertEquals(List.of("/opt/codekoll/extra-rules.jar"), config.strings("rules.rule-path"));
  }

  /** {@code --config} is named by the person running codekoll, so it is trusted the same way. */
  @Test
  void anExplicitConfigMaySetWhatTheRepoMayNot() throws IOException {
    Path explicit = userHome.resolve("mine.toml");
    Files.writeString(explicit, "[resolve]\nmode = \"build\"\n");

    Configuration config = ConfigLoader.load(repo, explicit, null, diagnostics);

    assertEquals("build", config.string("resolve.mode").orElseThrow());
  }
}
