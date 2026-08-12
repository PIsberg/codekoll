# Codekoll CLI — Specification: running codekoll against another repository

Companion to [SPEC.md](../SPEC.md) (which stays authoritative for the rule catalog, the engine
pipeline and the `Rule` SPI) and to [CLI-PLAN.md](CLI-PLAN.md) (build order). This document
specifies everything that is needed to turn `codekoll-cli` from "a jar you can point at a
directory" into a tool a stranger can run against *their* repository and trust the output of.

It supersedes SPEC §3.4 (Configuration) and §3.5 (CLI); those sections become pointers to this
document. Where this document and SPEC.md disagree on CLI surface, this document wins.

---

## 1. Goals & Non-Goals

### Goals

- **Zero build integration stays true.** `codekoll ~/src/some-repo` works on a freshly cloned
  repository with no edits to its build files. That claim is already in README §2; this spec is
  what makes it survive contact with a real repository.
- **No silent under-reporting.** Today a missing dependency makes javac attribution fail, the
  file is recorded as *skipped*, and every type-aware rule reports nothing for it. On a foreign
  repo this is the dominant failure mode and it currently looks identical to "clean". Attribution
  coverage becomes a first-class, gate-able output.
- **Scale to a real repository.** Tens of thousands of files, multiple modules with *different*
  classpaths and *different* language levels, in bounded memory.
- **Adoptable on legacy code.** A repository with 400 pre-existing findings must be able to gate
  CI on *new* findings from day one.
- **Correct paths.** Findings, JSON and SARIF must use repository-relative paths so GitHub code
  scanning annotates the right lines of the right files.
- **Installable.** A documented one-liner to get a working `codekoll` command, and a documented
  CI step.

### Non-Goals (v1 of the CLI work)

- Not a build plugin. No Maven/Gradle plugin is shipped (SPEC §11 territory).
- No IDE/LSP integration, no daemon, no watch mode.
- No compilation *output*: codekoll never writes class files into the target repo, never mutates
  it, and never checks anything out.
- No dependency *downloading* by codekoll itself. Codekoll may ask the target repo's own build
  tool for a classpath (§4), but it does not implement Maven/Gradle resolution or talk to
  artifact repositories.
- No cross-repository or monorepo-wide incremental cache in v1 (§9 caching is per-run).

---

## 2. Concepts

| Term | Meaning |
|---|---|
| **Target repo** | The repository being analyzed. May be anywhere on disk; never the codekoll checkout (except when dogfooding). |
| **Repo root** | The directory paths in reports are relative to. Detected (§3.1) or given via `--repo`. |
| **Source unit** | One compilation scope: a set of `.java` files + one classpath + one `--release` + optionally one module name. A Maven multi-module repo yields one source unit per module per source set. |
| **Attribution coverage** | `attributed files / discovered files` for a run. The honesty metric of §6. |
| **Workspace** | The result of discovery: repo root, build system, ordered list of source units, plus diagnostics about what could not be resolved. |

---

## 3. Workspace discovery

A new module `codekoll-workspace` (JPMS `io.codekoll.workspace`) owns everything in §3–§4. It
depends on `io.codekoll.api` only — **not** on `jdk.compiler` and **not** on `io.codekoll.engine`
— so the existing ArchUnit invariants (only the engine builds a `JavacTask`) are unaffected. The
CLI wires workspace → engine → report.

### 3.1 Repo root detection

In order, first match wins:

1. `--repo <dir>` if given.
2. The nearest ancestor of the *common prefix of the given paths* containing `.git`.
3. The nearest ancestor containing a build file (`pom.xml`, `settings.gradle[.kts]`,
   `build.gradle[.kts]`).
4. The common prefix of the given paths itself.

With no positional path at all, the target is the current working directory — `codekoll` in a
repo root is the intended everyday invocation.

### 3.2 Build-system detection

| Marker | Layout | Source units derived from |
|---|---|---|
| `pom.xml` | Maven, single or multi-module | `<modules>` recursion; per module `src/main/java` and (unless `--no-tests`) `src/test/java` |
| `settings.gradle[.kts]` / `build.gradle[.kts]` | Gradle, single or multi-project | `include(...)` entries in settings; per project `src/<sourceSet>/java` for the conventional source sets |
| neither, but `src/main/java` exists | Maven-conventional without a build file | that directory |
| neither | Plain source tree | every directory containing `.java` files, rooted at the shallowest such directory |

Detection is **textual and best-effort** — codekoll parses `pom.xml` with the JDK's XML parser
(external entities disabled, per the project's own CK-XXE-FACTORY rule) and reads Gradle settings
with a line regex for `include`. It never *evaluates* a build script. Anything it cannot
determine falls back to the plain-source-tree strategy, which is always correct if less precise.

### 3.3 File selection

Discovered under each source unit root:

- `**/*.java`, excluding `module-info.java` (current engine behaviour, unchanged).
- **Default exclusions**, applied before anything else and overridable via config:
  `**/target/**`, `**/build/**`, `**/out/**`, `**/bin/**`, `**/.git/**`, `**/.gradle/**`,
  `**/node_modules/**`, `**/generated/**`, `**/generated-sources/**`, `**/*.generated.java`,
  and any file whose first 5 lines contain `@Generated` or `// GENERATED — DO NOT EDIT`.
- `.gitignore` is honoured when the repo root is a git repo (`--no-gitignore` opts out).
  Implemented by pattern-matching the ignore files codekoll reads itself; codekoll does not
  shell out to git for this.
- Symlinks are not followed out of the repo root; cycles are detected and reported once.
- Files larger than 2 MiB are skipped with a warning (they are generated parsers, in practice).

`--include <glob>` / `--exclude <glob>` (repeatable) refine the set; `--exclude` wins over
`--include`, explicit CLI globs win over config, config wins over defaults.

### 3.4 Release level per unit

`--release` becomes a *fallback*, not a constant. Per source unit, in order:

1. `--release <n>` if explicitly given on the CLI (applies to all units, escape hatch).
2. Maven: `maven.compiler.release`, then `maven.compiler.source`, then
   `<configuration><release>` of `maven-compiler-plugin`, inherited from parent poms.
3. Gradle: `sourceCompatibility` / `java.toolchain.languageVersion` via line regex.
4. `.sdkmanrc` / `.java-version` / `.tool-versions` if present.
5. The running JDK's feature version.

A unit whose detected release is below javac's minimum supported `--release` is bumped to that
minimum, with a warning naming the unit. Detected values are reported in `--verbose` output;
guessing wrong here changes which rules can fire, so the guess is always visible.

---

## 4. Classpath resolution

Type-aware rules (the majority of the 114) need the target's dependencies on the classpath. This
section is the highest-value part of this spec.

### 4.1 Strategies

Selected with `--resolve <mode>`:

| Mode | Behaviour |
|---|---|
| `discover` | **Default.** Hermetic: no subprocess. Collects, per unit: existing `target/classes`, `target/test-classes`, `build/classes/java/*`, `build/libs/*.jar`, `lib/**/*.jar`, `libs/**/*.jar`, and the jars named by an existing `target/classpath.txt`-style file if one is present. Sibling modules' output directories are added to dependent modules. |
| `build` | Invokes the target repo's own build tool to print a classpath (§4.2). **Requires explicit opt-in** (§4.3). Falls back to `discover` on failure. |
| `auto` | `build` if opt-in was given, else `discover`. |
| `none` | Only `--classpath` / config; nothing is discovered. |

`--classpath <cp>` (existing flag) is always *appended* to whatever a strategy produced, so the
manual escape hatch composes instead of competing. A repeatable
`--module-classpath <unitSelector>=<cp>` sets a classpath for one unit in a multi-module repo.

### 4.2 Build-tool invocation

- **Maven**: `mvn -q -B --offline dependency:build-classpath -Dmdep.outputFile=<tmp> -DincludeScope=test`
  per module (one reactor invocation, output files collected). Falls back to online mode only if
  `--resolve-online` is given.
- **Gradle**: an *init script written by codekoll to a temp file* registering a
  `codekollClasspath` task that prints `configurations.compileClasspath` (+ `testCompileClasspath`
  unless `--no-tests`) per project, invoked as `gradle --offline -I <init> codekollClasspath`.
  The init script is codekoll's own code; the target's build files are still what get evaluated.
- Wrapper scripts (`./mvnw`, `./gradlew`) are preferred over anything on `PATH` when present.
- Bounded by `--resolve-timeout` (default 120 s, whole-resolution not per module). On timeout,
  non-zero exit, or unparsable output: warn, name the failing module, fall back to `discover`,
  continue. Resolution failure is never fatal on its own.
- Results are cached under `${XDG_CACHE_HOME:-~/.cache}/codekoll/classpath/<repo-hash>/<unit>.txt`,
  keyed by the mtime+size of the unit's build files. `--no-cache` bypasses; `--refresh-cache`
  rewrites.

### 4.3 Trust — mandatory

**Invoking the target repository's build tool executes arbitrary code from that repository**
(plugins, init scripts, `buildSrc`). This is materially different from reading source files, and
codekoll must not do it by surprise. Therefore:

- `build`/`auto`-with-build is never the effective default. It is enabled only by an explicit
  `--resolve build`, `--allow-build-execution`, or `resolve.mode = "build"` set in the **user's
  own** config (`~/.config/codekoll/config.toml`) — *never* by a `codekoll.toml` found inside the
  target repository. A target repo cannot opt itself into being executed.
- The first time `build` mode runs against a given repo root in an interactive terminal, codekoll
  prints the exact command line it is about to run and requires confirmation; `--yes` or any
  non-TTY (CI) skips the prompt, on the assumption that a CI operator who passed the flag meant it.
- `--resolve build` implies offline unless `--resolve-online` is also given, so a stale lockfile
  cannot silently pull artifacts.
- The chosen mode, the commands run, and their outcome appear in `--verbose` output and in the
  JSON/SARIF run metadata (§7.3).

---

## 5. Analysis execution

The engine currently builds **one** `JavacTask` for **all** files with **one** options list. That
is wrong for a multi-module repo (different classpaths) and unbounded in memory. Required changes:

- `CompilationDriver` gains `AnalysisResult analyzeUnits(List<SourceUnit>, List<Rule>)`. Each
  source unit gets its own `JavacTask` with its own `--release`, `-classpath` and
  `--module-path`/`--patch-module` when the unit is modular. `analyzePaths` stays as the simple
  single-unit convenience path.
- Units larger than `--batch-size` files (default 2000) are split into batches. Batching is
  per-unit, never across units, and never splits a package unless the package alone exceeds the
  batch size — cross-file symbol resolution within a package is worth keeping.
- Findings from all batches merge into one `AnalysisResult`; the existing sort is applied once at
  the end so output ordering is stable and independent of batching.
- **Determinism:** file discovery order is sorted; batch assignment is a pure function of the
  sorted list. Two runs over an unchanged repo produce byte-identical JSON output.
- Units are analyzed sequentially in v1 (SPEC §3.3's threading model is unchanged). `--jobs` is
  reserved but not implemented; the batching design is what makes adding it later mechanical.
- Memory: temporary class output goes to one temp directory per run, deleted on exit including on
  `SIGINT`. The driver never writes inside the target repo.
- **Timeout:** `--timeout <duration>` (default none) bounds the whole run; on expiry codekoll
  reports what it has, marks the run partial, and exits `2`.

---

## 6. Degraded-mode honesty

`AnalysisResult` gains counters: files discovered, files parsed, files attributed, files skipped
(with reasons grouped: syntax error, missing symbol, other), and rule failures (already present).

- Console output ends with an attribution line whenever coverage < 100 %:

  ```
  ⚠ 412 of 1,204 files could not be fully type-checked (66% attributed).
    Type-aware rules did not run on them. Most common cause: 3 unresolved imports
    (org.springframework.*, com.acme.internal.*, lombok.*).
    Try: --resolve build   (see codekoll --help resolve)
  ```

- `--min-attribution <pct>` (default `0`, i.e. off) makes the run exit `2` when coverage falls
  below the threshold. The recommended CI setting is `--min-attribution 90`.
- `--strict` (already promised by SPEC §3.3 for rule crashes) is extended: it also turns
  attribution failures and resolution failures into a non-zero exit.
- Skipped files are listed under `--verbose`, summarized otherwise. The current behaviour of
  printing one `skipped (does not compile): …` line per file is replaced — on a foreign repo it
  buries the findings.
- Lombok deserves a named diagnostic: if `lombok` appears among unresolved imports, say so
  explicitly and point at `--resolve build`, since annotation processing is disabled (`-proc:none`)
  and delomboked sources are the only path that works.

---

## 7. Output

### 7.1 Paths

All reported paths become **relative to the repo root**, using `/` separators on every platform.
Absolute paths appear only under `--absolute-paths`. This is a behaviour change for existing
output and is covered by updated snapshot tests. The repo root itself renders as `.`, never as an
empty string.

Implemented as a `PathRenderer` the CLI hands to the reporter: findings keep absolute paths
internally, and `codekoll-report` gains no dependency on the workspace model.

**Diagnostics are the exception.** A path inside a diagnostic — "could not parse
`<abs>/pom.xml`", "not Java source, ignored: `<abs>/build.gradle`" — stays absolute. These name a
file on disk that codekoll failed to use, often one outside the repo root, and an unambiguous
path is worth more there than a consistent one. Snapshot tests normalize it.

### 7.2 Console

Unchanged in shape (README §quick-start example stays accurate), plus: a header line naming the
repo root, build system and unit count under `--verbose`, and the §6 attribution footer.
`--quiet` suppresses everything except findings; `--no-color`/`NO_COLOR` as already specified.

### 7.3 SARIF

- `artifactLocation.uri` is the repo-relative path; `uriBaseId` is `%SRCROOT%`, with
  `originalUriBaseIds` set from the repo root. This is what GitHub code scanning needs to
  annotate a PR; the current absolute-path emission does not annotate reliably.
- `automationDetails.id` = `codekoll/<pack-filter-or-all>` so multiple codekoll runs on one repo
  do not overwrite each other's code-scanning results.
- `invocation` records: codekoll version, resolve mode, attribution coverage, unit count,
  `executionSuccessful`, and any resolution failures as `toolExecutionNotifications`.
- `partialFingerprints.codekollBaselineId/v1` = the baseline identity of §8, so GitHub can track
  a finding across line-number churn.
- Rule metadata (`description`, `explanation`, `fix`) already flows from the `Rule` SPI; add
  `helpUri` pointing at the rule's anchor in the generated catalog.

### 7.4 JSON

Adds a top-level `run` object mirroring the SARIF `invocation` data above, and a `summary` object
(counts by severity, by pack, attribution). Findings keep their existing shape plus the baseline
id. The JSON format is declared **stable** at this point and gets a `schemaVersion` field.

---

## 8. Baseline — adopting codekoll on an existing repo

- `--baseline <file>` (conventionally `.codekoll-baseline.json`, committed to the target repo):
  findings present in the baseline are **suppressed from output and from the exit code**, and
  counted in a summary line (`312 findings suppressed by baseline`).
- `--write-baseline <file>` runs the analysis and writes every current finding as the new
  baseline, then exits `0`. `--update-baseline` rewrites in place *and* prunes entries that no
  longer reproduce, so the baseline shrinks as code is fixed and never rots upward.
- **Baseline identity** must survive reformatting and line drift. Identity =
  `sha256(ruleId + " " + repoRelativePath + " " + normalizedSnippet)` where
  `normalizedSnippet` is the finding's snippet with all whitespace runs collapsed to one space and
  leading/trailing whitespace removed. Line and column are stored for reporting but are **not**
  part of the identity. Multiple identical findings in one file are disambiguated by an occurrence
  index.
- Consequence, stated plainly: moving a file or editing the offending line un-suppresses the
  finding. That is the intended trade-off — it fails toward showing a finding, never toward hiding
  one.
- The baseline file records the codekoll version that wrote it. Reading a baseline written by a
  different rule-catalog version warns but proceeds.

## 9. Changed-files mode

`--changed-since <git-ref>` restricts *reported* findings to files changed relative to that ref
(`--changed-since origin/main` being the PR use case). Analysis still compiles the full source
unit — cross-file symbols are needed for attribution — but findings in untouched files are
filtered out and counted. `--changed-lines-only` narrows further to lines in the diff hunks.

Implemented by reading git's plumbing output (`git diff --name-only <ref>...HEAD`) via a
subprocess; git is optional, and its absence degrades to "analyze everything" with a warning.
This is reading, not building, so it is not gated by §4.3.

---

## 10. Configuration

Extends SPEC §3.4. Resolution order, later overriding earlier:

1. Built-in defaults.
2. User config: `${XDG_CONFIG_HOME:-~/.config}/codekoll/config.toml` (the only place
   `resolve.mode = "build"` may be enabled — §4.3).
3. Target repo config: `<repo-root>/codekoll.toml`, else `<repo-root>/.codekoll.toml`, else
   `<repo-root>/.config/codekoll.toml`.
4. `--config <file>` if given (replaces step 3, does not merge with it).
5. Environment: `CODEKOLL_*` for a small allowlist (`CODEKOLL_CONFIG`, `CODEKOLL_CACHE_DIR`,
   `NO_COLOR`).
6. CLI flags.

```toml
[rules]
disable = ["CK-CRYPTO-WEAK"]
disable-packs = ["performance"]
enable-only = []                     # if non-empty, an allowlist

[severity]
"CK-THREAD-RUN" = "error"

[suppress]
paths = ["**/generated/**", "**/legacy/**"]

[sources]
include = ["src/main/java"]          # overrides discovery entirely when set
exclude = ["**/build/**"]
tests = true
gitignore = true

[compile]
release = 21                         # overrides detection for all units
classpath = "libs/foo.jar"

[resolve]
mode = "discover"                    # "build" only honoured from the user config
timeout = "120s"

[report]
format = "console"
fail-on = "error"
min-attribution = 0
baseline = ".codekoll-baseline.json"
```

Unknown keys are an error, not a silent no-op — a typo'd rule id in `disable` must not quietly
leave the rule on. `--print-config` dumps the fully merged effective configuration with the
provenance of each value, which is the first thing to ask for in a bug report.

---

## 11. CLI surface

```
codekoll [OPTIONS] [<path>...]        # default path: the current directory

Target
  --repo <dir>              repo root for path relativization and config lookup
  --include <glob>          keep only discovered sources matching this (repeatable)
  --exclude <glob>          drop sources (repeatable)
  --no-tests                skip test source sets
  --no-gitignore            do not honour .gitignore

Compilation
  --release <n>             override detected language level for all units
  --classpath <cp>          appended to every unit's resolved classpath
  --module-classpath <u>=<cp>   per-unit classpath (repeatable)
  --resolve <mode>          discover|build|auto|none            (default discover)
                            build and auto are refused until the §4.3 trust gate exists
  --allow-build-execution   permit invoking the target's build tool (see --resolve build)
  --resolve-online          allow the build tool to hit the network (implies not --offline)
  --resolve-timeout <dur>   default 120s
  --batch-size <n>          files per javac invocation           (default 2000)
  --timeout <dur>           bound the whole run

Rules
  --rules <ids>             comma list, allowlist
  --packs <names>           comma list, allowlist
  --rule-path <jars>        extra module path entries scanned for third-party rule packs
  --explain <id>            print explanation, fix and example, then exit
  --catalog                 print the rule catalog as Markdown, then exit

Output
  --format console|json|sarif                                   (default console)
  --output <file>
  --absolute-paths          report absolute instead of repo-relative paths
  --quiet / --verbose
  --no-color
  --fail-on error|warning|never                                 (default error)
  --min-attribution <pct>   fail when type-check coverage is below this
  --strict                  rule crashes and resolution failures are fatal

Adoption
  --baseline <file>
  --write-baseline <file>
  --update-baseline
  --changed-since <ref>
  --changed-lines-only

Diagnostics
  --print-config            effective config with per-value provenance, then exit
  --print-workspace         detected repo root, build system, units, classpaths, then exit
                            human-readable by default; --format json emits the machine-readable
                            form the fixture-repository snapshots assert against
  --no-cache / --refresh-cache
  --version / --help
```

**Wildcards on Windows.** The `java` launcher expands a command-line argument containing `*` when
it matches files on disk, so `--exclude "codekoll-examples/**"` can arrive as one glob plus a
handful of positional paths — quoting in the shell does not prevent it. Codekoll copes (non-source
files are reported and dropped, overlapping paths are de-duplicated) but the exclusion silently
does less than the user asked. A pattern that matches nothing literally (`**/examples/**`), the
launcher scripts of §12, or `codekoll.toml` all avoid it.

Exit codes:

| Code | Meaning |
|---|---|
| `0` | No findings at or above the `--fail-on` threshold (after baseline filtering) |
| `1` | Findings at or above the threshold |
| `2` | Usage error, internal error, `--min-attribution` unmet, `--strict` violation, or timeout |

Codes `1` and `2` are deliberately distinguishable so CI can tell "the tool found bugs" from
"the tool could not do its job" — the two must never be conflated on a foreign repo.

---

## 12. Distribution

The release workflow currently attaches `codekoll.jar` to a GitHub release. That is the payload;
it is not yet an install story. Additions:

- **Launcher scripts** (`codekoll`, `codekoll.bat`) generated at build time: locate a JDK 25+
  (`JAVA_HOME`, then `PATH`), fail with a clear message if only a JRE is found (the engine already
  raises "run codekoll on a JDK, not a JRE" — the launcher should say it before the JVM starts),
  and exec the jar with `CODEKOLL_JAVA_OPTS` honoured.
- **Install one-liner**: a `install.sh` / `install.ps1` in the release that downloads the jar +
  launcher into `~/.local/bin` (or `%LOCALAPPDATA%\Programs\codekoll`), verifying a published
  SHA-256 checksum. Checksums and (once signing exists) provenance attestations are release assets.
- **jbang catalog** (`jbang codekoll@codekoll <path>`) — near-zero-effort given a published jar.
- **Container image** `ghcr.io/<org>/codekoll:<version>`, JDK 25 base, entrypoint the launcher,
  documented `docker run --rm -v "$PWD:/src" ghcr.io/<org>/codekoll /src`.
- **GitHub Action** (`.github/actions/codekoll` in this repo, usable as `<org>/codekoll@v1`):
  inputs `path`, `fail-on`, `format`, `baseline`, `resolve`, `min-attribution`; outputs the SARIF
  file path, with `upload-sarif: true` wiring `github/codeql-action/upload-sarif` for the caller.
- **Homebrew tap and SDKMAN!** are explicitly deferred until after a v0.1.0 release exists.

Every distribution channel ships the same jar; none of them may embed a second copy of the rules.

---

## 13. Testing strategy

Extends SPEC §9.

- **Fixture repositories** under `codekoll-cli/src/test/resources/repos/`, each a tiny but real
  layout: `maven-single`, `maven-multimodule`, `gradle-groovy`, `gradle-kts-multiproject`,
  `plain-sources`, `no-build-file`, `mixed-release` (modules at 17 and 21), `broken-pom`,
  `missing-deps` (imports that cannot resolve — the degraded-mode fixture). Each has an expected
  workspace JSON asserted against `--print-workspace`.
- **Golden output tests** for console/JSON/SARIF over a fixture repo, with the SARIF validated
  against the SARIF 2.1.0 schema.
- **Baseline round-trip tests**: write baseline → assert zero findings → edit an unrelated file →
  assert still zero → introduce a new bug → assert exactly one finding → `--update-baseline` prunes
  a fixed finding.
- **Path-relativization tests on both separators** (the Windows case is not theoretical — this
  project is developed on Windows and released on Linux).
- **Resolution tests** run in `discover` mode by default in CI; `build`-mode tests are tagged and
  run in a dedicated job that has Maven and Gradle available, so the main suite stays hermetic.
- **Scale test**: `codekoll-load-test` gains an external-repo corpus tier — one generated
  multi-module 100 kLOC repository — asserting bounded heap with `--batch-size` and that batching
  does not change the finding set versus a single-batch run.
- **Dogfooding**, extended: the existing `selfcheck` gate additionally runs codekoll on codekoll
  *through the new workspace discovery path* (`codekoll .` from the repo root), proving discovery
  works on at least one real multi-module Maven repo on every build.

---

## 14. Security considerations

- §4.3 trust gate is the load-bearing control: reading a repo is safe, building it is not.
- Config files inside the target repo are **untrusted input**: they may not enable build
  execution, may not set `--rule-path` (loading code from a path the repo controls), and may not
  redirect `--output` outside the repo root. Violations are reported as errors naming the key.
- `--rule-path` loads third-party code into codekoll's JVM. It is CLI/user-config only, documented
  as such.
- XML parsing of `pom.xml` disables DTDs and external entities (codekoll's own CK-XXE-FACTORY rule
  applies to codekoll).
- Codekoll never writes into the target repo. Class output, temp files and caches live in the OS
  temp directory or the user cache directory.
- Secrets: findings include source snippets, and `CK-HARDCODED-SECRET` findings by definition
  quote credentials. SARIF/JSON output is therefore treated as sensitive; the snippet for that
  rule is redacted to the first 8 characters plus `…` in machine-readable output.

---

## 15. Open questions

1. Whether `--resolve discover` should also read `.classpath` (Eclipse) and `.idea/libraries`
   (IntelliJ) — cheap, but rewards stale IDE metadata. Deferred, not rejected.
2. Whether the baseline should live in the target repo or in a codekoll-side store. Current answer
   is target repo (reviewable in PRs); revisit if the file gets large.
3. Bazel/Buck support: out of scope, but the `SourceUnit` abstraction is deliberately shaped so a
   fourth detector can be added without touching the engine.
