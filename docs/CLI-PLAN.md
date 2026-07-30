# Codekoll CLI — Implementation Plan: running codekoll against another repository

Companion to [CLI-SPEC.md](CLI-SPEC.md). Continues the milestone numbering of
[PLAN.md](../PLAN.md), which ends at Milestone 10 (reporting, docs, CI, release). Milestones here
are ordered so that **every one of them ends with a strictly better foreign-repo experience** than
the one before — there is no milestone whose only value is "groundwork for the next".

**The standing quality bar and the standing dogfooding gate from PLAN.md apply unchanged.** Every
milestone below finishes with `mvn verify` green: Checkstyle, PMD, SpotBugs+findsecbugs, Error
Prone, NullAway/JSpecify, ArchUnit, JaCoCo (≥ 90 % line / ≥ 85 % branch), plus codekoll analyzing
its own sources with `--fail-on error`.

> **Open discrepancy, needs a decision (found during Milestone 11).** That coverage bar is
> documented here, in PLAN.md and in `SKILL.md`, but it is *not* what the build enforces:
> `pom.xml` sets `jacoco.line.minimum` to `0.50` and `jacoco.branch.minimum` to `0.40`. A green
> JaCoCo gate therefore proves much less than these documents claim. Measured today:
> workspace 92.7 / 85.4, report 86.7 / 62.2, rules 83.0 / 68.7, cli 55.3 / 44.1 (line / branch %),
> so raising the properties to 0.90/0.85 now would turn three modules red. Either the thresholds
> get raised module-by-module as coverage lands, or these documents should stop claiming a number
> the build does not hold anyone to. Not silently changed either way — it is a project decision.

**Three additional standing agreements for this workstream:**

1. **Every milestone is validated against at least three real third-party repositories**, not only
   fixtures. Suggested standing corpus, all permissively licensed and structurally different:
   a Maven multi-module library, a Gradle-Kotlin-DSL project, and a Spring Boot application.
   Triage every finding; every false positive becomes a negative fixture before it is fixed
   (PLAN.md working agreement, unchanged).
2. **No milestone may make the tool quieter.** Any change that could reduce the finding count on
   the standing corpus must be explained in the PR description with numbers before and after.
3. **Fail toward visible, never toward silent.** When codekoll cannot do something (resolve a
   classpath, attribute a file, parse a build file), the run says so in the output. This is the
   thread running through Milestones 11–16 and it is the whole point of the workstream.

---

## Field validation log

Standing agreement 1 asks every milestone to be validated against real third-party repositories.
Runs are recorded here: what was run, what came back, and what changed as a result.

### 2026-07-30 — vibetags (Maven, multi-module) and async-test-lib (Gradle Kotlin DSL)

Both repos analyzed with the built jar. Neither has a root aggregator that codekoll can use yet, so
the classpath was assembled by hand: `mvn dependency:build-classpath` per module for vibetags, a
Gradle init script printing `sourceSets.main.compileClasspath` for async-test-lib. That manual step
is the work Milestones 11-13 exist to remove, and it is not optional: without `--classpath`,
6 files were skipped in vibetags and 13 in async-test-lib, which is most of the latter's public API.

| Repo | Findings | Errors | Warnings | Info | Skipped |
| --- | --- | --- | --- | --- | --- |
| vibetags | 15 | 0 | 8 | 7 | 0 |
| async-test-lib | 27 | 2 | 20 | 5 | 5 (IntelliJ SDK absent from the offline cache) |

Triage:

- **Both ERRORs were false positives**, the same idiom in two files: `s == s.intern()` and
  `obj == ((String) obj).intern()` in async-test-lib's `SynchronizedOnLiteralDetector` and
  `BoxedPrimitiveLockDetector`. Identity is the question being asked; `equals()` would answer a
  different one and always be true. One site already carried `@SuppressFBWarnings` and
  `@SuppressWarnings("ReferenceEquality")` with a comment saying so. Fixed as an exemption in
  CK-REF-EQUALITY, with the two shapes plus a still-flagged control (`a == b.intern()`) added as
  fixtures first, per the working agreement.
- **The `--output` file was not machine-readable.** Skipped-file diagnostics were written to the
  reporter's stream after the closing bracket, so `--format json --output f.json` produced a file
  that will not parse and `--format sarif` would have produced one GitHub code scanning rejects.
  Diagnostics now go to stderr, where a terminal still shows them. This only surfaced because a
  foreign repo had files codekoll could not compile; codekoll's own sources never do.
- **Two more false positives, both about where an expression actually runs.**
  CK-REGEX-IN-LOOP flagged `for (String t : args.split("[,;]"))` in async-test-lib's
  `AgentOptions` and vibetags' `RoleConfig`: the sequence expression of a for-each runs once,
  before the first iteration, so the regex is compiled once. `insideLoop()` now skips a loop
  entered through its header (for-each expression, basic-for initializer) and keeps looking for
  an outer loop, so the same call nested one level in still reports. CK-RESOURCE-LEAK flagged
  `TelemetryBridge.activate()`, a factory that registers the bridge and returns it: the rule
  already treated a returned *creation expression* as ownership transfer but not a returned
  *local*. Both have fixtures pinning the exempt shape and a still-reported control.
- **Best true positive:** async-test-lib `LazyInitValidator` performs `|=` on four `volatile
  boolean` fields from a method called concurrently by design. Fixed upstream.

- **Third false positive, same family:** CK-RESOURCE-LEAK flagged vibetags' `ForkJoinPool`,
  which is released with `shutdown()` in a `finally` block. Disposal is not spelled `close()`
  everywhere, and for an executor `shutdown()` is the ordinary idiom, so the rule now accepts
  `close`, `shutdown`, `shutdownNow`, `dispose` and `release`. A pool that is never released at
  all still reports (fixture P11).

Net effect, with nothing lost that was worth keeping (standing agreement 2):

| Repo | Before | After codekoll fixes | After upstream fixes |
| --- | --- | --- | --- |
| vibetags | 15 (0 error, 8 warning, 7 info) | 12 | 7, all INFO |
| async-test-lib | 27 (2 error, 20 warning, 5 info) | 23 | 4, all INFO |

Every removal was triaged as a false positive first and pinned by a fixture before the rule
changed; the rest were fixed upstream, on a branch in each repo, and both repos now credit
codekoll with a README badge. What remains in both is INFO only, and each item was read and
accepted rather than suppressed: a deliberate `catch (Throwable)` in an annotation processor,
`printStackTrace` in a Byte Buddy agent's `onError` where no logger is safe, `contains()` on
short config lists, four private records built only from immutable lists, an
`ObjectInputStream` read of a file the same process wrote, and one float comparison against an
exact sentinel.

---

## Milestone 11 — Workspace model, source discovery, honest paths (3–4 days)

The foundation, but immediately user-visible: after this milestone `codekoll .` in a foreign repo
root does the right thing instead of walking `target/`.

**Status: the discovery library is done and tested; the CLI surface that exposes it is not.**
Everything under "Library" below is implemented, 110 tests, line 92.7 % / branch 85.4 % coverage.
Nothing under "User-visible surface" is started, so from a user's point of view this milestone has
not yet landed — `codekoll` still walks the paths it is given and prints absolute paths.

Library:

- [x] **New module `codekoll-workspace`** (JPMS `io.codekoll.workspace`), Maven module +
      `module-info.java` from the start, per the project's JPMS-first rule. **Deviation, deliberate:
      it requires `java.xml` only — not `io.codekoll.api`.** Discovery turned out to need no api
      type at all, and the tighter boundary is asserted by `WorkspaceArchitectureTest`.
- [x] `RepoRoot` detection (CLI-SPEC §3.1) and `BuildSystem` detection (§3.2): XXE-hardened Maven
      pom parsing, Gradle settings scanning, conventional-layout and plain-tree fallbacks.
      **Deviation:** discovery is layout-first — it finds modules by walking for `src/<set>/java`
      rather than by recursing `<modules>`/`include(...)`. A layout is a fact on disk; a build file
      is a claim. `PomReader.modules()` and `GradleReader.subprojects()` are implemented and tested
      but not yet consumed; they are for cross-checking the layout in `--print-workspace`.
- [x] `SourceUnit` record (roots, classpath, release, module name) and `Workspace` (repo root,
      build system, ordered units, discovery diagnostics).
- [x] File selection (§3.3): default exclusions, `.gitignore` honouring (own matcher, no git
      subprocess), generated-file detection, symlink-cycle safety, 2 MiB cap, `--exclude` globs.
      **Not done:** `--include` — `WorkspaceOptions.includes()` exists but nothing reads it.
- [x] Release-level detection per unit (§3.4), every fallback covered, every guess and every clamp
      recorded in `diagnostics()`. Surfacing it in `--verbose` waits on the CLI work.
- [x] Hermetic `discover`-mode classpath resolution (§4.1), brought forward from Milestone 13
      because per-unit classpaths were cheap once `SourceUnit` existed.
- [x] **ArchUnit additions**: `io.codekoll.workspace..` may not depend on `jdk.compiler`,
      `io.codekoll.engine..`, `io.codekoll.report..` or `io.codekoll.rules..`, may not write to
      `System.out`/`err`, may not call `System.exit`, and may not touch `ProcessBuilder` (the
      §4.3 gate). Placed in the workspace module so they travel with the code they constrain.
- [x] **ARCHITECTURE.md**: module map updated to eight modules; `SKILL.md` module map likewise.

User-visible surface (remaining Milestone 11 work):

- [ ] **Repo-relative paths everywhere** (§7.1): `Finding.file()` stays absolute internally, the
      reporters relativize against the repo root. `--absolute-paths` restores the old behaviour.
      Update the existing reporter snapshot tests — this is a deliberate output change.
      `Workspace.relativize()` already exists and is tested; nothing calls it yet.
- [ ] `--repo`, `--print-workspace`, `--no-tests`, `--no-gitignore`, `--include`, `--exclude`,
      `--absolute-paths` on the CLI; positional path defaults to `.`. The CLI does not reference
      `codekoll-workspace` at all yet, so this is also where the two modules first get wired.
- [ ] Fixture repositories (§13) for `maven-single`, `maven-multimodule`, `gradle-groovy`,
      `gradle-kts-multiproject`, `plain-sources`, `no-build-file`, `broken-pom`, each with an
      asserted `--print-workspace` snapshot. **Partly covered differently:** the same layouts are
      already asserted as programmatic fixtures in `WorkspaceDiscoveryTest`; what is missing is the
      committed-repo form and the snapshot of CLI output, both of which need `--print-workspace`.
- [ ] Translate `SourceUnit` → `AnalysisUnit` in the CLI (the engine-side records exist already).

**Exit criterion:** `codekoll --print-workspace` on all seven fixture repos and on the three
standing corpus repos produces a correct unit list; `codekoll .` from the codekoll repo root
discovers exactly the production source roots (no `target/`, no generated sources) and reports
repo-relative paths; the dogfooding gate runs through this path. **Not met yet** — blocked only on
the CLI items above.

## Milestone 12 — Configuration, rule filtering, `--rule-path` (2–3 days)

Closes the two commitments SPEC.md already makes but the code does not keep (§3.4 config file,
§3.5 `--rule-path`), now with the layering a foreign repo needs.

- [ ] TOML reader. **Decision required and recorded in ARCHITECTURE.md**: a small hand-rolled
      reader for the strict subset the schema uses (keeps the "picocli is the only runtime
      dependency" property) versus adding a TOML library. Recommendation: hand-rolled subset
      parser in `codekoll-workspace`, ~200 lines, with a hostile-input fixture suite — the schema
      is small and closed, and a second runtime dependency is a real cost for a tool distributed
      as a single jar.
- [ ] Config resolution order and merging (CLI-SPEC §10), with **per-value provenance** tracked
      from the start (it is far harder to add later) and surfaced by `--print-config`.
- [ ] Full schema: `[rules]` (incl. `enable-only`), `[severity]`, `[suppress]`, `[sources]`,
      `[compile]`, `[resolve]`, `[report]`. Unknown keys and unknown rule ids are **errors**.
- [ ] Severity overrides applied to `Finding.severity()` before reporting and before exit-code
      computation.
- [ ] **Untrusted-repo-config enforcement** (§14): a `codekoll.toml` inside the target repo may not
      set `resolve.mode = "build"`, may not set `rule-path`, and may not redirect output outside
      the repo root. Each violation is a named error, each has a test.
- [ ] `--rule-path <jars>`: ServiceLoader over a separate `ModuleLayer`/`URLClassLoader`, third-party
      rules validated against the same metadata contract as built-ins (non-empty
      `description`/`explanation`/`fix`, a `RulePack`) and rejected with a clear message otherwise.
- [ ] `--print-config` and a documented `codekoll.toml` reference section.

**Exit criterion:** a `codekoll.toml` in a fixture repo demonstrably disables a pack, raises a
severity and excludes a path; `--print-config` shows where every effective value came from; a
malicious fixture repo config attempting build execution and rule-path injection is rejected with
both violations named; a sample third-party rule pack jar loads via `--rule-path` and fires.

## Milestone 13 — Classpath resolution (4–5 days)

The milestone that decides whether type-aware rules actually work on someone else's code.

- [ ] **`discover` strategy** (§4.1) first, since it is hermetic and always available: existing
      build outputs, `build/libs`, `lib/**`, sibling-module output directories.
- [ ] Wire resolved classpaths into per-unit analysis (depends on Milestone 14's engine change if
      that lands first; otherwise a single merged classpath is an acceptable interim for
      single-module repos and **must be called out as interim in the PR**).
- [ ] **`build` strategy** (§4.2): Maven `dependency:build-classpath` per reactor, Gradle via a
      codekoll-authored init script, wrapper scripts preferred, offline by default, bounded by
      `--resolve-timeout`, failure always degrading to `discover` and never fatal.
- [ ] **Trust gate** (§4.3) implemented exactly as specified: user-config/CLI opt-in only, target
      repo cannot self-enable, interactive confirmation showing the literal command line,
      `--yes`/non-TTY bypass, mode recorded in output metadata. **Write these tests before the
      feature** — this is the one place in the workstream where a bug executes attacker-controlled
      code.
- [ ] Classpath cache keyed by build-file mtime+size under the user cache dir; `--no-cache`,
      `--refresh-cache`.
- [ ] `--classpath` appends rather than replaces; `--module-classpath <unit>=<cp>` per unit.
- [ ] Resolution tests split into a hermetic default suite and a tagged `build`-mode suite running
      in a CI job that provisions Maven and Gradle.

**Exit criterion:** on the standing corpus, attribution coverage in `discover` mode is measured and
recorded, and `--resolve build` raises it to ≥ 95 % on all three repos; the trust gate is proven by
a test asserting that a target-repo config setting `resolve.mode = "build"` does **not** cause a
subprocess to run.

## Milestone 14 — Multi-unit analysis, batching, degraded-mode honesty (3–4 days)

- [ ] `CompilationDriver.analyzeUnits(List<SourceUnit>, List<Rule>)`: one `JavacTask` per unit with
      its own release and classpath; `analyzePaths` retained as the single-unit convenience path.
- [ ] Batching within a unit (`--batch-size`, default 2000), package-aware, deterministic; results
      merged and sorted once. **Test: batched and unbatched runs produce identical finding sets.**
- [ ] Temp class-output directory per run, cleaned on normal exit and on `SIGINT`; nothing written
      inside the target repo (asserted by a test that snapshots the fixture repo's file tree
      before and after a run).
- [ ] `--timeout` for the whole run; partial results reported, exit `2`.
- [ ] **Attribution accounting** (§6): counters on `AnalysisResult`, skipped-file reasons grouped,
      the unresolved-import summary, the named Lombok diagnostic.
- [ ] Console attribution footer, `--min-attribution <pct>`, `--strict` extended to attribution and
      resolution failures. Replace the per-file `skipped (does not compile)` spam with the summary.
- [ ] `codekoll-load-test`: external-repo corpus tier (generated multi-module 100 kLOC repo),
      asserting bounded heap under batching and no finding-set change versus a single batch.
      Baseline updated **in its own PR with the reason stated**, per the standing agreement.

**Exit criterion:** the `mixed-release` fixture (modules at 17 and 21) analyzes correctly in one
run; a 100 kLOC generated repo completes within the `-Xmx1g` load-test budget; the `missing-deps`
fixture reports an honest attribution percentage and exits `2` under `--min-attribution 90`
instead of silently reporting zero findings.

## Milestone 15 — Adoption: baseline and changed-files mode (2–3 days)

- [ ] Baseline identity function (§8): `sha256(ruleId + path + normalized snippet)` plus occurrence
      index. Property test: identity is stable under reindentation and line insertion above the
      finding, and changes when the offending line's tokens change.
- [ ] `--baseline`, `--write-baseline`, `--update-baseline` (prunes non-reproducing entries), the
      suppressed-count summary line, and version-mismatch warning on read.
- [ ] Baseline filtering applied before exit-code computation and before SARIF emission;
      `partialFingerprints.codekollBaselineId/v1` in SARIF so GitHub tracks findings across churn.
- [ ] `--changed-since <ref>` and `--changed-lines-only` via `git diff --name-only <ref>...HEAD`;
      git absent or not a repo degrades to analyzing everything **with a warning**, never silently.
- [ ] Round-trip tests per §13.

**Exit criterion:** on the largest standing-corpus repo, `--write-baseline` followed by a clean run
exits `0`; introducing one new bug exits `1` reporting exactly that finding; fixing a
baselined finding and running `--update-baseline` shrinks the file.

## Milestone 16 — Distribution, CI integration, documentation (3–4 days)

- [ ] Launcher scripts `codekoll` / `codekoll.bat` generated at build time, JDK-not-JRE detection
      *before* JVM start, `CODEKOLL_JAVA_OPTS` honoured, attached to the release.
- [ ] `install.sh` / `install.ps1` with published SHA-256 checksums; checksums as release assets.
- [ ] jbang catalog entry.
- [ ] Container image `ghcr.io/<org>/codekoll:<version>` on JDK 25, entrypoint the launcher,
      published by the release workflow, with the documented `-v "$PWD:/src"` invocation.
- [ ] **GitHub Action** in this repo (inputs `path`, `resolve`, `fail-on`, `format`, `baseline`,
      `min-attribution`; SARIF output wired to code scanning), with its own smoke-test workflow
      that runs the action against a fixture repo.
- [ ] **SARIF completeness** (§7.3): `uriBaseId`/`originalUriBaseIds`, `automationDetails.id`,
      `invocation` metadata, `helpUri` per rule; validated against the SARIF 2.1.0 schema in CI.
- [ ] **JSON format declared stable** with `schemaVersion`, `run` and `summary` objects (§7.4).
- [ ] Secret redaction for `CK-HARDCODED-SECRET` snippets in machine-readable output (§14).
- [ ] **Docs**: a new "Run codekoll on your repository" section in README.md (install, first run,
      what to do about attribution warnings, CI snippet, baseline adoption walkthrough);
      `docs/CLI-SPEC.md` linked from SPEC §3.4/§3.5, which are reduced to pointers; ARCHITECTURE.md
      final pass; the project skill (`.claude/skills/codekoll/SKILL.md`) updated with the new
      module and commands.

**Exit criterion:** a person with only a JDK installed can go from the release page to a finding on
their own repository in under two minutes, following only the README; the GitHub Action smoke test
is green and its SARIF appears in the code-scanning tab; SARIF validates against the schema.

---

## Sequencing notes

- **11 → 12 → 13/14 → 15 → 16** is the dependency order. Milestones 13 and 14 are genuinely
  parallelizable (resolution vs. execution) if two people are working; if one, do **14 before 13**,
  because per-unit analysis is what makes per-unit classpaths meaningful, and the interim
  single-classpath fallback noted in Milestone 13 is then unnecessary.
- Milestone 12 could technically be deferred, but it is placed early on purpose: config provenance
  and untrusted-config enforcement are both far cheaper to build in than to retrofit, and every
  later milestone adds flags that want config equivalents.

## Total estimate

~17–23 working days. Risk concentrates in Milestone 13 (foreign build tools misbehave in
creative ways, and the trust gate must be exactly right) and Milestone 11's Gradle detection
(build scripts are programs; codekoll only reads them textually and must degrade gracefully when
that is not enough).

**De-scoping levers, in the order they should be pulled:**

1. Drop Gradle `build`-mode resolution (keep Gradle *discovery*) — Maven covers the larger share of
   the Java-source-tree world and the init-script path is the fiddliest part of Milestone 13.
2. Drop `--changed-lines-only` (keep `--changed-since`).
3. Drop the container image and jbang catalog (keep jar + launcher + install script).

None of these change the architecture; each is a leaf.

**What must not be de-scoped:** the §4.3 trust gate and the §6 attribution honesty. A tool that
silently executes a stranger's build, or that silently reports "clean" because it could not
type-check anything, is worse than no tool — and both failures are invisible to the person running
it, which is precisely why they cannot be traded away for schedule.

## Working agreements (in addition to PLAN.md's)

- Changes to CLI flags, config keys or output formats update `docs/CLI-SPEC.md` **in the same PR**,
  and SPEC §3.4/§3.5 stay pointers to it — one authority for the CLI surface.
- Any new subprocess codekoll can invoke is documented in CLI-SPEC §14 and gated per §4.3.
- Every user-visible failure mode gets a message that names the cause **and** a next action
  (`--resolve build`, `--print-workspace`, `--print-config`). "Failed to resolve classpath" alone
  is not an acceptable message.
- Output-format changes (paths becoming relative, skipped-file summary replacing per-file lines)
  ship with updated snapshot tests in the same commit, never as a follow-up.
