package io.codekoll.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * The attribution footer (CLI-SPEC §6): a run that could not type-check every file says so, in
 * numbers, after the report.
 *
 * <p>Before it, a repository whose dependencies were missing produced one
 * {@code skipped (does not compile)} line per file and then a clean-looking total. On vibetags,
 * 89 of 765 files went unanalyzed under "0 error(s), 0 warning(s), 1 info", and the one-line
 * total was the only part anyone reads.
 */
class AttributionFooterCliTest {

  @TempDir
  Path repo;

  private record Run(int exitCode, String report, String stderr) {}

  private void source(String name, String text) throws IOException {
    Path file = repo.resolve("src/main/java/app/" + name + ".java");
    Files.createDirectories(file.getParent());
    Files.writeString(file, text, StandardCharsets.UTF_8);
  }

  private Run run(String... args) throws IOException {
    Path file = repo.resolve("out-" + System.nanoTime() + ".txt");
    String[] all = new String[args.length + 7];
    System.arraycopy(args, 0, all, 0, args.length);
    all[args.length] = "--fail-on";
    all[args.length + 1] = "never";
    all[args.length + 2] = "--repo";
    all[args.length + 3] = repo.toString();
    all[args.length + 4] = "--output";
    all[args.length + 5] = file.toString();
    all[args.length + 6] = repo.toString();
    StringWriter err = new StringWriter();
    CommandLine cmd = new CommandLine(new Main());
    cmd.setErr(new PrintWriter(err, true));
    int code = cmd.execute(all);
    return new Run(code, Files.exists(file) ? Files.readString(file) : "", err.toString());
  }

  @Test
  void aRunWithUnresolvedImportsEndsWithTheCountAndWhatWasMissing() throws IOException {
    source("Ok", "package app; class Ok {}");
    source("NeedsLib", "package app; import org.missing.one.Thing; class NeedsLib { Thing t; }");
    source("NeedsLibToo", "package app; import org.missing.one.Other; class NeedsLibToo {}");
    source("NeedsOther", "package app; import com.absent.Lib; class NeedsOther {}");

    Run run = run();

    assertEquals(0, run.exitCode());
    assertTrue(run.stderr().contains(
            "warning: 3 of 4 files could not be type-checked (25% type-checked)"),
        run.stderr());
    assertTrue(run.stderr().contains("no rule ran on them"), run.stderr());
    assertTrue(run.stderr().contains(
            "Unresolved, by files affected: org.missing.one (2 files), com.absent (1 file)"),
        run.stderr());
    assertTrue(run.stderr().contains("--classpath"), run.stderr());
  }

  @Test
  void skippedFilesAreSummarizedUnlessVerbose() throws IOException {
    source("Ok", "package app; class Ok {}");
    source("NeedsLib", "package app; import org.missing.one.Thing; class NeedsLib {}");

    Run quiet = run();
    Run verbose = run("--verbose");

    assertFalse(quiet.stderr().contains("skipped (does not compile)"), quiet.stderr());
    assertTrue(quiet.stderr().contains("--verbose lists every skipped file"), quiet.stderr());
    assertTrue(verbose.stderr().contains("skipped (does not compile): src/main/java/app/NeedsLib"),
        verbose.stderr());
    assertTrue(verbose.stderr().contains("1 of 2 files could not be type-checked"),
        verbose.stderr());
  }

  @Test
  void aFullyTypeCheckedRunPrintsNoFooter() throws IOException {
    source("Ok", "package app; class Ok {}");

    Run run = run();

    assertFalse(run.stderr().contains("could not be type-checked"), run.stderr());
  }

  @Test
  void lombokIsNamedBecauseItsMembersNeverExistUnderProcNone() throws IOException {
    source("Ok", "package app; class Ok {}");
    source("Bean", "package app; import lombok.Data; @Data class Bean { int id; }");

    Run run = run();

    assertTrue(run.stderr().contains("lombok (1 file)"), run.stderr());
    assertTrue(run.stderr().contains("Lombok is among them"), run.stderr());
    assertTrue(run.stderr().contains("delombok"), run.stderr());
  }

  @Test
  void theFooterNeverLandsInAMachineReadableReport() throws IOException {
    source("NeedsLib", "package app; import org.missing.one.Thing; class NeedsLib {}");

    Run run = run("--format", "json");

    assertEquals("[]", run.report().replaceAll("\\s", ""), run.report());
    assertTrue(run.stderr().contains("could not be type-checked"), run.stderr());
  }

  @Test
  void aLongListOfMissingPackagesIsCutAndSaysHowManyMore() throws IOException {
    for (int i = 1; i <= 7; i++) {
      source("Needs" + i, "package app; import org.missing.p" + i + ".X; class Needs" + i + " {}");
    }

    Run run = run();

    assertTrue(run.stderr().contains("and 2 more"), run.stderr());
  }
}
