# Codekoll — Implementation Plan

Companion to [SPEC.md](SPEC.md). Milestones are ordered so that every milestone ends with a runnable, testable tool. Rules land in waves: the nine founding rules first (easiest → hardest, per SPEC §5), then the extended catalog (SPEC §6) in two difficulty waves — **106 rules total** across ten packs. (That was the v1 target and is what the Milestone 6–10 exit criteria below are written against; the catalog has since grown to **114 specified rules** — see the SPEC §4 table, which is authoritative for the current count.)

**Standing quality bar (applies to every milestone, not just the last one):**
Codekoll is a static analysis tool, so it must hold itself to the standard it enforces on others. From Milestone 0 onward, `mvn verify` runs and **fails the build** on any violation from: Checkstyle, PMD, SpotBugs (+findsecbugs), Error Prone, NullAway (JSpecify nullness), ArchUnit, and JaCoCo coverage thresholds (**≥ 90 % line / ≥ 85 % branch** on production modules). No milestone is complete while any gate is red or suppressed without a written justification comment.

**Standing dogfooding gate (from Milestone 2 onward):**
Codekoll analyzes **its own production sources** as part of every `mvn verify`, with `--fail-on error` — not just in CI at the end, but locally, from the moment the first real rule exists. Wired via an `exec-maven-plugin` step in a `selfcheck` profile bound to `verify`: build the jar, run it over `codekoll-api/engine/rules/report/cli` sources. Each new rule immediately confronts real code (its first corpus is codekoll itself); a finding in our own code is either a real bug (fix it, and note it — every self-caught bug is pitch material) or a false positive (which becomes a negative fixture per the working agreements). The Milestone 10 `selfcheck` CI job is this same step plus SARIF upload, not a separate mechanism.

---

## Where we are — 2026-08-12

Status of `main` at commit `8b2cdcd`, established by reading the tree rather than the history:

| | State |
|---|---|
| Rules | **110 implemented** of **114 specified** (SPEC §4). Registered in `module-info`, one example class each, all ten packs present. |
| Milestones 0–5 | Complete. Engine, dispatcher, suppression, ArchUnit suite, ARCHITECTURE.md, all nine founding rules. |
| Milestones 6–7 | Complete except four rules, listed below. |
| Milestone 8 | Complete — 110 example classes, verification suite, generated `docs/RULES.md`. |
| Milestone 9 | Complete — `codekoll-load-test`, `baseline.json`, `docs/perf/*.png`, `loadtest` CI job. Baselines are **per environment** (`ci-linux`, `dev-windows`): comparing a CI run against a developer machine's milliseconds failed three pull requests that could not have changed performance, one of them Markdown-only. The gate has now been **demonstrated red** on a seeded slowdown, +24.2 % CPU against a +15 % budget. |
| Milestone 10 | **Partial.** Reporters, `--explain`, `--catalog`, seven CI jobs and the generated catalog are done. Four items are not. |
| Milestones 11–16 | Separate workstream in [docs/CLI-PLAN.md](docs/CLI-PLAN.md). Milestone 11's library half is written but **is not on `main`** (see below). |

**The four unimplemented rules** (specified in SPEC §6, no implementation, no fixtures, no example):
`CK-ARRAY-AS-KEY` and `CK-WALLCLOCK-ELAPSED` (`correctness`, Milestone 6 Wave A),
`CK-FUTURE-DISCARDED` (`concurrency`, Wave A), `CK-PARALLEL-MUTATION` (`concurrency`, Milestone 7
Wave B). Per-pack counts show it directly: SPEC says `correctness` 28 / `concurrency` 14,
`docs/RULES.md` reports 26 / 12.

**What Milestone 10 still owes**, each verified absent in the code rather than inferred:

- `codekoll.toml` loading. SPEC §3.4 specifies the file and §3.5 specifies `--config`; nothing in
  `codekoll-cli` reads either. Milestone 12 of the CLI plan is where this now lands. Note that
  README §"How to run" tells users to "configure per-project via `codekoll.toml`" — the one place
  in the docs where a missing feature is described to a user as working.
- `--rule-path`. Specified in SPEC §3.5, absent from the CLI and the engine. Also Milestone 12.
- Console color, `--no-color`, `NO_COLOR`. No occurrence of `NO_COLOR` in `codekoll-report` or
  `codekoll-cli`.
- Live badges. The README's badge row is eight hand-written shields.io placeholders, not workflow
  status badges, so a red gate leaves them green — the one thing the milestone said badges must
  not do. The seven CI jobs they should point at do exist.

**Two open discrepancies that need a decision, not a doc edit:**

1. **Milestone 11's library half is stranded on a branch.** `feat/workspace-discovery` (commit
   `3a3c2e4`, 3 730 lines: the `codekoll-workspace` module, 110 tests, and **`docs/CLI-SPEC.md`**)
   was merged into `fix/output-purity-and-intern-idiom` an hour *after* that branch had already
   merged to `main`, so PR #14 reads as merged while none of its content reached `main`. Two
   consequences on `main` today: the eighth module does not exist, and `docs/CLI-PLAN.md` opens by
   calling itself a companion to a `docs/CLI-SPEC.md` that is not in the repository. Recovering it
   is a cherry-pick of `3a3c2e4` onto `main`, in its own PR.
2. **The coverage bar the documents claim is not the one the build enforces.** This preamble,
   `docs/CLI-PLAN.md` and `SKILL.md` all say ≥ 90 % line / ≥ 85 % branch; `pom.xml` sets
   `jacoco.line.minimum` to `0.50` and `jacoco.branch.minimum` to `0.40`. Raising the properties
   today turns three modules red (measured in the CLI plan). Either the thresholds climb
   module-by-module or the documents stop claiming a number nobody is held to. Unchanged here on
   purpose — it is a project decision, and quietly editing either side would hide it.

~~**Not verified in this pass:** that the load-test gate actually fails on a seeded slowdown.~~
**Verified since.** A seeded ~25 % slowdown in `CompilationDriver` was reported as
`REGRESSION: CPU +24.2% exceeds +15% budget`; removing the seed put the same run back inside the
budget. `codekoll-load-test` still has no test directory — this particular check can only be
honest by being run.

## Milestone 0 — Project skeleton + quality gates (1–1½ days)

- [x] **Multi-module Maven project**, `--release 25`, JUnit 5 — seven modules per SPEC §3.1, each production module with a `module-info.java` from day one (retrofitting JPMS is far harder than starting with it):
  - `codekoll-api` → `io.codekoll.api` (SPI: `Rule`, `RulePack`, `Finding`, `RuleId`, `Severity`, `FindingCollector`; zero dependencies).
  - `codekoll-engine` → `io.codekoll.engine` (`requires io.codekoll.api, jdk.compiler`; `uses io.codekoll.api.Rule`).
  - `codekoll-rules` → `io.codekoll.rules` (`provides io.codekoll.api.Rule with …`; exports nothing).
  - `codekoll-report` → `io.codekoll.report` (`requires io.codekoll.api` only — must NOT require `jdk.compiler`).
  - `codekoll-cli` → `io.codekoll.cli` (picocli, config, wiring; fat-jar entry point).
  - `codekoll-examples` → deliberately **no** `module-info` (represents typical user code).
  - `codekoll-load-test` → no `module-info` (internal perf harness, never shipped; fleshed out in Milestone 9).
- [x] Fat-jar packaging (`maven-shade-plugin`) on `codekoll-cli`, `Main-Class` manifest. (Shading flattens the module graph for the jar; JPMS still pays off at compile time, for ArchUnit-style enforcement, and for a later `jlink` image.)
- [x] **Wire all static-quality tooling from day one** (retrofitting is 10× the cost):
  - **Checkstyle** (`maven-checkstyle-plugin`, Google style as base, checked-in `config/checkstyle.xml`) — fails the build.
  - **PMD** (`maven-pmd-plugin`, default + `bestpractices` + `errorprone` categories, checked-in ruleset XML) — fails the build.
  - **SpotBugs** (`spotbugs-maven-plugin`, effort=Max, threshold=Low, plus the `findsecbugs` plugin) — fails the build.
  - **Error Prone** (javac compiler plugin; errors fail the build) with **NullAway** in JSpecify mode.
  - **JSpecify**: `@NullMarked` on every package via `package-info.java`; `@Nullable` where needed; enforced by NullAway, not just decorative.
  - **JaCoCo**: `jacoco:check` bound to `verify` with the thresholds above.
  - **ArchUnit** test suite (grows over time; see Milestone 1) — architecture rules as executable tests, complementing what JPMS already enforces at compile time.
  - Verify each tool actually runs on **JDK 25** during the spike; pin versions that do. If a tool lags JDK 25 support, document the pinned workaround in ARCHITECTURE.md — never silently disable the gate.
- [x] Initial GitHub Actions workflow: `mvn verify` on JDK 25 (full pipeline lands in Milestone 10).
- [x] Decide CLI approach: picocli dependency vs hand-rolled arg parsing (recommendation: picocli — it's the only runtime dependency and earns its keep).

**Exit criterion:** `java -jar codekoll.jar --help` prints usage **and** `mvn verify` runs Checkstyle, PMD, SpotBugs, Error Prone/NullAway, JaCoCo check, and the (initially minimal) ArchUnit suite — all green in CI, across all seven modules.

## Milestone 1 — Compilation driver + rule engine (2–3 days)

The riskiest technical piece; do it first and de-risk everything after.

- [x] `CompilationDriver` in `io.codekoll.engine`: collect `.java` files from CLI paths (honoring `[suppress].paths` globs), build one `JavacTask` with user classpath + `--release`, run `parse()` + `analyze()`.
- [x] Handle files that fail attribution: report as skipped with the javac diagnostic, continue.
- [x] `Rule` SPI in `io.codekoll.api` — including the **self-documentation contract**: `description()`, `explanation()` (what is wrong + what happens at runtime), and `fix()` (how to fix it). These feed the README catalog, example READMEs, SARIF descriptors, `--explain`, and finding messages; a metadata test rejects empty values.
- [x] **ServiceLoader-based registry** in the engine (`ServiceLoader.load(Rule.class)`), plus `--rule-path` support for third-party rule-pack modules.
- [x] **Combined-traversal dispatcher** (SPEC §10): each rule declares the `Tree.Kind`s it subscribes to; the engine walks each compilation unit **once** and dispatches nodes to subscribed rules. With ~90 rules, per-rule full passes are not acceptable. Design this now — it shapes `AbstractRule`.
- [x] `AbstractRule` (internal to `io.codekoll.rules`) with type-helper methods: `isSubtypeOf`, `isStringType`, `resolvedType`, `sourceSnippet`, `enclosingLoop`, `constantValue`, `sameSymbol`.
- [x] Suppression: `@SuppressWarnings("codekoll:…")` lookup up the tree path; `// codekoll:off` line comments via source-position scan.
- [x] Console reporter (plain, no color yet) in `io.codekoll.report`.
- [x] Fixture-test harness: compile-from-string, run one rule, assert `// :: finding-here` markers.
- [x] **ArchUnit rules** (added now that there are packages to constrain — these encode what JPMS can't express, and double-check what it can):
  - `io.codekoll.rules..` may not depend on `io.codekoll.report..` (rules emit `Finding`s, they don't format them).
  - Only `io.codekoll.engine..` may create/hold a `JavacTask`.
  - **No `com.sun.tools.javac.*` imports anywhere** (public compiler API only).
  - `io.codekoll.cli..` is the only package allowed to call `System.exit` / write to `System.out` directly.
  - Every concrete `Rule` implementation is registered in `module-info` `provides` (reflection test), has a `RulePack`, and has non-empty `description`/`explanation`/`fix`.
- [x] **ARCHITECTURE.md (first complete draft)** — written now, while decisions are fresh, and kept current in every later milestone. Required contents:
  - Module map: the six Maven/JPMS modules, their `requires`/`provides`/`uses` relationships, and why `codekoll-examples` is intentionally non-modular.
  - The compilation pipeline: how `JavacTask` is configured, parse vs analyze phases, why attributed trees are required, how attribution failures are handled.
  - The combined-traversal dispatcher: node-kind subscription model and its performance rationale.
  - The `Rule` SPI contract (including the self-documentation metadata), ServiceLoader discovery, and the lifecycle of a `Finding`.
  - Threading model (v1: single-threaded per compilation; where parallelism could later go).
  - Extension guide: "how to add a rule" and "how to ship a third-party rule pack" walkthroughs.
  - Design decisions & rejected alternatives (JavaParser vs javac API, source vs bytecode, per-rule passes vs combined dispatch, JPMS vs classpath) with rationale.
  - The ArchUnit rules listed above, each cross-referenced to the test that enforces it.

**Exit criterion:** a trivial demo rule runs end-to-end via ServiceLoader discovery and combined dispatch; harness green; ArchUnit suite enforcing the constraints above; ARCHITECTURE.md reviewed and complete for the code that exists.

**Known risk & mitigation:** `com.sun.source` is a JDK-supplied API in the `jdk.compiler` module; `requires jdk.compiler` in the engine's `module-info` makes access explicit (`com.sun.source.*` is exported; `com.sun.tools.javac.*` internals are NOT to be touched). Spike this in the first hour of the milestone.

## Milestone 2 — Founding syntax-shape rules (1 day)

- [x] **CK-EMPTY-CATCH** (SPEC §5.1) — pure AST shape; `ignored`-name and `InterruptedException` exemptions + tests.
- [x] **CK-THREAD-RUN** (§5.2) — first use of type resolution.
- [x] **CK-CRYPTO-WEAK** (§5.3) — literal + constant-folded argument matching, blocklist table, ECB/NoPadding INFO checks.

- [x] **Wire the standing dogfooding gate** (see preamble): `selfcheck` profile running the built jar over codekoll's own production sources with `--fail-on error`, bound to `mvn verify` from this milestone forward.

**Exit criterion:** 3 rules green on positive+negative fixtures; the dogfooding gate runs in `mvn verify` and codekoll's own source is clean under it; coverage thresholds still met.

## Milestone 3 — Founding type-aware rules (2–3 days)

- [x] **CK-IGNORED-RETURN** (§5.4) — known-pure method table as data (a resource file, not code).
- [x] **CK-REF-EQUALITY** (§5.5) — the exemption set is the bulk of the work; write negative fixtures first.
- [x] **CK-STR-CONCAT-LOOP** (§5.6) — loop-scope tracking (declared inside vs outside loop).

**Exit criterion:** 6 rules green; run against one real OSS codebase and manually triage every finding — fix any false positive found before proceeding.

## Milestone 4 — Founding flow-ish rules (3–4 days)

- [x] **CK-RESOURCE-LEAK** (§5.7) — layered: try-with-resources detection → ownership-transfer exemptions → `close()`-in-finally search → no-op allowlist → factory-openers table.
- [x] **CK-IMPOSSIBLE-COND** (§5.8) — null-fact collector over `&&`/`||` chains, conservative invalidation, constant contradictions. **Build the fact machinery as a reusable engine utility** — the Wave B nullness rules (CK-NON-SHORT-CIRCUIT and friends) reuse it.

**Exit criterion:** 8 rules green; OSS-corpus triage repeated; false positives fixed or exempted with tests.

## Milestone 5 — Founding generics rule (2 days)

- [x] **CK-GENERIC-MISMATCH** (§5.9) — `Types.asMemberOf` recovery of `K`/`E`; both-ways `isAssignable`; boxed-form comparison; raw/wildcard/`Object` bail-outs. **Extract the "provably unrelated types" check as a shared helper** — `CK-EQUALS-INCOMPATIBLE` (Wave B) reuses it.

**Exit criterion:** 9/9 founding rules green.

## Milestone 6 — Extended catalog, Wave A: shape & simple-type rules (8–10 days)

Seventy-three rules that need only AST shape plus straightforward type lookups — high volume, low individual risk. Batch them by pack so shared helpers and fixtures amortize; land as one PR per pack.

- [ ] `correctness` (23): CK-SELF-ASSIGN, CK-SELF-COMPARE, CK-EQUALS-HASHCODE, CK-EQUALS-OVERLOAD, CK-EQUALS-NULL-ARG, CK-COLLECTION-SELF-ADD, CK-INFINITE-RECURSION, CK-SB-CHAR-CTOR, CK-WEEK-YEAR-FORMAT, CK-ARRAY-OBJECT-METHODS, CK-TOSTRING-ARRAY, CK-NAN-COMPARE, CK-FORMAT-MISMATCH (includes the small format-specifier parser — the one genuinely fiddly item in this wave), CK-ASSIGN-IN-COND, CK-BIGDECIMAL-DOUBLE, CK-BIGDECIMAL-EQUALS, CK-EXCEPTION-NOT-THROWN, CK-OPTIONAL-NULL, CK-URL-EQUALS, CK-SWITCH-FALLTHROUGH, CK-DEFAULT-CHARSET, CK-ARRAY-AS-KEY, CK-WALLCLOCK-ELAPSED. (CK-EQUALS-INCOMPATIBLE is the pack's one Wave B rule.) — **outstanding: CK-ARRAY-AS-KEY, CK-WALLCLOCK-ELAPSED**; the rest of the pack, including rules added to SPEC after this list was written, is done.
- [x] `numeric` (8): CK-INT-OVERFLOW-WIDEN, CK-COMPARE-SUBTRACT, CK-ABS-OVERFLOW, CK-SHIFT-OOB, CK-INT-DIV-FLOAT, CK-FLOAT-EQUALITY, CK-DIV-ZERO, CK-OCTAL-LITERAL.
- [ ] `concurrency` (partial, 8): CK-SYNC-ON-VALUE, CK-MONITOR-ON-LOCK, CK-VOLATILE-COMPOUND, CK-WAIT-NO-LOOP, CK-STATIC-DATEFORMAT, CK-SLEEP-IN-SYNC, CK-CTOR-THREAD-START, CK-FUTURE-DISCARDED. — **outstanding: CK-FUTURE-DISCARDED.**
- [x] `resources` (7): CK-THROW-IN-FINALLY, CK-LOST-CAUSE, CK-CATCH-NPE, CK-CATCH-BROAD, CK-PRINT-STACKTRACE, CK-SYSTEM-EXIT, CK-FINALIZE.
- [x] `security` (partial, 5): CK-HARDCODED-SECRET (name-regex + placeholder exemptions), CK-INSECURE-RANDOM, CK-WEAK-TLS, CK-PLAIN-HTTP, CK-NATIVE-DESERIAL.
- [x] `performance` (5): CK-REGEX-IN-LOOP, CK-KEYSET-GET, CK-BOXED-ACCUMULATOR, CK-CONTAINS-IN-LOOP, CK-NEW-WRAPPER.
- [x] `api-misuse` (partial, 5): CK-TOARRAY-CAST, CK-REGEX-META-LITERAL, CK-LOCALE-CASE, CK-REMOVE-INT-AMBIGUOUS, CK-TOMAP-DUPLICATES.
- [x] `modern` (partial, 7): CK-SEALED-SWITCH-DEFAULT, CK-RECORD-ARRAY-COMPONENT, CK-RECORD-MUTABLE-COMPONENT, CK-VT-POOLING, CK-VT-DAEMON-PRIORITY, CK-CHRONO-UNSUPPORTED, CK-DURATION-CALENDAR. (Differentiated pack — see SPEC §1 *Prior art* — so its fixtures deserve extra care: incumbents offer no reference behavior to compare against.)
- [x] `frameworks` (partial, 5): CK-PROXY-ANNOTATION-INVISIBLE, CK-INJECT-STATIC, CK-ENTITY-CONTRACT, CK-TEST-INVISIBLE (all annotation + modifier shape checks), CK-SLF4J-PLACEHOLDER (reuses the CK-FORMAT-MISMATCH parser). Fixtures need the framework annotations available: add provided-scope test-only deps (spring-context, jakarta.persistence, junit, slf4j) to the fixture harness classpath — analysis targets, not runtime dependencies of codekoll itself. The same OSS-corpus triage rule applies doubly here: run against a real Spring codebase before calling the pack done.

**Exit criterion:** all Wave A rules green on fixtures; OSS-corpus triage run over the *whole* rule set (noise compounds — this is the milestone where precision tuning matters most); combined-dispatch traversal profiled to confirm the single-pass design holds at ~85 rules.

## Milestone 7 — Extended catalog, Wave B: heuristic & multi-node rules (7–8 days)

Twenty-four rules that correlate multiple AST regions or reuse shared machinery — each needs individual design care.

- [x] `correctness`: CK-EQUALS-INCOMPATIBLE (reuses the M5 unrelated-types helper).
- [ ] `concurrency`: CK-DCL-NO-VOLATILE (shape-match the double-check idiom), CK-LOCK-NO-FINALLY, CK-CONCURRENT-MOD (loop-variable/receiver symbol matching), CK-PARALLEL-MUTATION (lambda-capture analysis over parallel stream chains). — **outstanding: CK-PARALLEL-MUTATION.**
- [x] `security`: CK-SQL-CONCAT (taint-shaped heuristic: non-constant parts in concatenation feeding JDBC sinks), CK-EXEC-CONCAT (same machinery, process sinks), CK-XXE-FACTORY (method-scope hardening-call correlation), CK-TRUST-ALL (empty-body method detection on TLS interfaces, incl. lambdas), CK-REDOS (the nested-quantifier grammar over parsed regex literals — small but genuinely fiddly).
- [x] `api-misuse`: CK-IMMUTABLE-MUTATE (origin tracking through direct assignment chains), CK-COMPUTE-IF-ABSENT-MOD (same-symbol modification inside the compute lambda).
- [x] `nullness`: CK-NON-SHORT-CIRCUIT (reuses the M4 null-fact collector), CK-UNBOX-NPE, CK-NULLABLE-CHAIN, CK-OPTIONAL-OF-NULLABLE (these three share the known-nullable method list, shipped as data), CK-NULL-TO-NONNULL (reads JSpecify/JSR-305 annotations off `Elements` — no inference), CK-OVERRIDE-NULLNESS (walks the overridden-method pair's annotations), CK-OPTIONAL-GET-BARE (direct-chain only).
- [x] `modern`: CK-STRUCTURED-GET-BEFORE-JOIN, CK-STREAM-REUSE, CK-ARENA-USE-AFTER-CLOSE (all statement-order analyses over a single method body).
- [x] `frameworks`: CK-PROXY-SELF-INVOKE (class-local: this-dispatch calls to annotated methods of the same class), CK-LOG-EXCEPTION-LOST (catch-block/log-call correlation).

**Exit criterion:** all 106 rules green; full OSS-corpus triage; every false positive converted to a negative fixture before fix.

## Milestone 8 — Examples subproject + end-to-end verification (3–4 days)

The `codekoll-examples` module is a small, realistic "buggy application" that demonstrates **every rule firing on real code** — living documentation and the end-to-end test bed. With 106 rules this only stays maintainable because it is machine-verified, not hand-audited.

- [x] **One example class per rule** (106 classes), organized in one package per pack (`examples.correctness`, `examples.numeric`, …), each named after its rule (`SelfAssignExample`, `UnboxNpeExample`, …). **Every example class documents itself** with a mandatory, uniform structure:
  - Class Javadoc with three sections, populated from the same facts as the rule's metadata:
    - **What is wrong** — the mistake, in the context of the example's scenario.
    - **What happens at runtime** — the concrete failure (`StackOverflowError`, always-false comparison, one-year date shift, SQL injection, …).
    - **How to fix it** — the corrected approach, matching the correct variant below.
  - A `buggy()` method containing the bug in a realistic setting, the offending line marked `// :: CK-…`.
  - A `fixed()` method with the corrected code, commented to highlight exactly what changed — and which must NOT be flagged.
- [x] Example sources compile as part of the normal build (they are *runtime* bugs — compiling cleanly is the point).
- [x] **Example verification suite** (`codekoll-examples/src/test/…`), one parameterized test over the rule registry:
  1. every rule fires at exactly its marked lines in its example class,
  2. every `fixed()` variant produces zero findings,
  3. **registry-completeness:** every rule discovered via ServiceLoader has an example class (a future 107th rule cannot ship without one),
  4. **pack-completeness:** every `RulePack` has a package here,
  5. **doc-completeness:** every example class Javadoc contains the three mandatory sections, and every rule's `explanation()`/`fix()` are non-empty (checked here as well as in ArchUnit, so the failure points at the offending rule).
- [x] Exempt `codekoll-examples` from the *external* quality gates where they'd correctly object to the intentional bugs (SpotBugs/PMD/Error Prone will flag many of the same patterns — that overlap is expected and is a nice implicit cross-validation): scoped, per-file suppressions with a `// intentional bug: demonstrates CK-…` comment, never blanket module-level disabling.
- [x] `codekoll-examples/README.md`: **generated** per-pack tables — rule → example file → what is wrong → how to fix (columns sourced from `description()`/`explanation()`/`fix()` metadata).

**Exit criterion:** `mvn verify` builds the examples and the verification suite proves all 106 rules fire on documented examples and stay silent on the fixed variants.

## Milestone 9 — Load-test harness & performance baselines (3–4 days)

SPEC §10 sets performance targets; this milestone makes them **measured, charted, and regression-gated** in the dedicated `codekoll-load-test` module. The first recorded run is **baseline v0 — the current state of the project** at the moment the harness lands; every version after that becomes a data point on the same charts.

- [x] **`codekoll-load-test` module** (plain, never shipped): benchmark driver, corpus generator, chart generator.
- [x] **Corpora:**
  - `corpus-small` — ~50 representative files, checked in (sampled from fixtures + examples so every pack's node kinds are exercised).
  - `corpus-large` — generated **deterministically** at build time by a seeded template generator (100 kLOC and 500 kLOC tiers); never committed, always reproducible from the seed.
- [x] **Measurement protocol** (comparability over cleverness):
  - Analyzer runs in a **forked JVM** with pinned flags and fixed heap (`-Xmx1g`) so numbers are comparable across machines and versions.
  - Per run, capture: wall time, **process CPU time** (`OperatingSystemMXBean`), **peak used heap** after a forced GC (`MemoryMXBean`), allocated bytes (`ThreadMXBean`), and the findings count (sanity check that two versions actually did the same work).
  - 2 warm-up + 3 measured iterations, median reported.
- [x] **Two profiles:**
  - `quick` — small corpus + one 100 kLOC run, ≤ ~2 min total: runs **on every CI build**.
  - `full` — all tiers, 5 iterations, optional JFR recording for deep dives: nightly schedule + before each release.
- [x] **Results & baseline:** one JSON per run keyed by version/SHA under `codekoll-load-test/results/`; `baseline.json` is committed — **baseline v0 is recorded immediately from the project as it stands**, and is only ever refreshed deliberately, in its own PR, with the reason in the commit message (same discipline as every other gate).
- [x] **Regression gate:** the `quick` profile **fails the build** when CPU time regresses > 15 % or peak heap > 20 % vs `baseline.json`. Gate on CPU time, not wall time — shared CI runners make wall time too noisy to gate on.
- [x] **Diagram images per version:** a chart generator (XChart or JFreeChart — plain Java, no services) renders PNG/SVG: per-version bar charts for CPU time, peak heap, and throughput (kLOC/s), plus a trend line across all recorded versions. Written to `docs/perf/`; committed on release tags (the README embeds the latest trend image), uploaded as workflow artifacts on PR builds.
- [x] **CI wiring:** new `loadtest` job (quick profile) on every push/PR; a scheduled nightly workflow runs `full`; the release workflow appends the tagged version's data point and regenerates the `docs/perf/` images.

**Exit criterion:** baseline v0 committed together with the first generated charts of the current project state; the `loadtest` CI job is green **and** demonstrably red on a seeded 20 % slowdown (prove the gate actually gates before trusting it).

## Milestone 10 — Reporting, docs, CI pipeline & release (2–3 days)

### Reporting & UX
- [x] SARIF 2.1.0 reporter + JSON reporter, snapshot-tested (pack + explanation/fix in rule metadata).
- [ ] Console color, `--no-color`, `NO_COLOR`; `--explain <id>` (prints explanation, fix, and the example's buggy/fixed snippets). — `--explain` done; **color and `NO_COLOR` not started** (no occurrence in `codekoll-report` or `codekoll-cli`).
- [ ] `codekoll.toml` config loading (disable / disable-packs / severity / suppress-paths), CLI override precedence. — **not started.** Nothing reads a config file; moved to CLI-PLAN Milestone 12, which specifies the layering a foreign repo needs. SPEC §3.4 and README both describe this as if it works.
- [ ] `--fail-on` exit-code logic; `--packs` / `--rules` filters; `--rule-path` third-party pack loading. — `--fail-on`, `--packs`, `--rules` done; **`--rule-path` not started** (CLI-PLAN Milestone 12).

### README.md (complete, top-level)

A pitch-first README.md already exists at the repo root (prior-art positioning + pack overview); this milestone completes it with the items below and replaces its hand-written rule summaries with the generated catalog.

- [ ] **Badge row at the very top**, one badge per gate, all wired to real signals (no decorative badges): — **not met.** Eight hand-written shields.io placeholders sit where the workflow-status badges should be, so every gate reads green regardless of CI.
  - Build (GitHub Actions workflow status badge for the `build` job).
  - Tests + Examples (badge for the `examples` verification job).
  - Coverage (JaCoCo percentage — via Codecov upload or a generated-badge action committed to a badges branch).
  - Checkstyle, PMD, SpotBugs, Error Prone/NullAway (JSpecify), ArchUnit — one passing badge each, backed by the named CI jobs below so a red gate turns its badge red.
- [ ] **How to run**: prerequisites (JDK 25), download/build the jar, full CLI reference (every flag from SPEC §3.5), `codekoll.toml` reference, suppression syntax, exit codes, CI-integration snippet (GitHub Actions step + SARIF upload to code scanning). — section written, but it **documents a `codekoll.toml` that does not exist** and points at SPEC §3.4–3.5, which document `--config` and `--rule-path` the CLI does not have. Honest until the config work lands, or the claims come out.
- [x] **How it works**: a readable summary of the pipeline (javac parse → attribute → combined single-pass rule dispatch → findings → reporters) with a diagram, plus a short "why JPMS + ServiceLoader" note, linking to ARCHITECTURE.md for depth.
- [x] **Rule catalog section**: table of all 106 rules grouped by pack (ID, severity, what is wrong, how to fix, link to the example file in `codekoll-examples`) — **generated from `Rule` metadata** by a small build step so docs can't drift. — the generated catalog is `docs/RULES.md` (`codekoll --catalog`); the README carries a hand-written per-pack summary table and links to it.
- [x] Quick-start: "run codekoll on `codekoll-examples` and see all 106 findings" as the 30-second demo.
- [x] Final ARCHITECTURE.md review pass: reporters, config, examples module, and the Wave A/B rule infrastructure added; diagrams current.

### GitHub Actions pipeline (`.github/workflows/ci.yml`)
- [x] Trigger on push + PR. Named jobs (names are load-bearing — badges point at them):
  1. **build** — `mvn verify` on the production modules: compile + unit/fixture tests.
  2. **quality** — Checkstyle, PMD, SpotBugs, Error Prone/NullAway as separate steps so failures are attributable per-tool.
  3. **coverage** — JaCoCo report + threshold check + badge/Codecov upload.
  4. **archunit** — architecture test suite.
  5. **examples** — build `codekoll-examples` and run the example verification suite (all 106 rules fire, fixed variants silent, docs complete).
  6. **selfcheck** — run the built codekoll jar on its own production sources with `--fail-on error` (dogfooding gate) and upload the SARIF to GitHub code scanning.
  7. **loadtest** — the Milestone 9 quick profile: measure CPU/memory against `baseline.json`, fail on regression, upload the generated chart images as artifacts.
- [ ] Nightly scheduled workflow: `full` load-test profile (Milestone 9). — **not created.** The only scheduled workflows in `.github/workflows/` are `codeql` and `scorecards`.
- [x] Release workflow on tag: build fat jar, attach to GitHub release, append the version's load-test data point and regenerate `docs/perf/` diagrams.

**Exit criterion:** all seven CI jobs green on `main`; README badges render and reflect them; SARIF from `selfcheck` visible in the repo's code-scanning tab; v0.1.0 tagged with the fat jar attached and its perf data point charted in `docs/perf/`. — **partially met:** the seven jobs exist and `v0.1.0` is tagged; the badges do not reflect anything, and the nightly profile and the four Reporting & UX gaps above are open.

## Milestones 11–16 — Running codekoll against another repository

Milestones 0–10 above deliver the analyzer and its rule catalog. Making that jar usable against
*someone else's* repository — workspace discovery, dependency-classpath resolution, honest
reporting when type-checking degrades, baselines for legacy code, and an install story — is
planned separately in **[docs/CLI-PLAN.md](docs/CLI-PLAN.md)**, specified in
**[docs/CLI-SPEC.md](docs/CLI-SPEC.md)**.

| # | Milestone | Estimate |
|---|---|---|
| 11 | Workspace model, source discovery, repo-relative paths | 3–4 d |
| 12 | Configuration layering, `--rule-path`, untrusted-config enforcement | 2–3 d |
| 13 | Classpath resolution (`discover` / `build`) + trust gate | 4–5 d |
| 14 | Per-unit analysis, batching, attribution honesty | 3–4 d |
| 15 | Baseline + changed-files mode | 2–3 d |
| 16 | Distribution, GitHub Action, docs | 3–4 d |

The standing quality bar, the dogfooding gate and the working agreements of this document apply
unchanged to all of them.

---

## Total estimate

~40–51 working days for v0.1.0 with all 106 rules. Schedule risk concentrates in Milestone 1 (engine + dispatcher spike), Milestone 4 (heuristic-rule triage), and Milestone 6 (precision tuning across the Wave A volume).

**De-scoping lever if needed:** waves are internally ordered so the tool is releasable after any pack completes — a v0.1.0 with the 9 founding rules + the `correctness` and `numeric` packs (~41 rules) is a coherent cut; remaining packs become v0.2.x without architectural change.

## Working agreements

- A rule is "done" only with positive **and** negative fixtures — the negative set encodes the precision policy (SPEC §8) — **and** an entry in `codekoll-examples` with its documented buggy/fixed pair and verification test.
- Any false positive found in corpus triage becomes a negative fixture before it's fixed.
- **Dogfooding is permanent:** codekoll runs on its own sources in every `mvn verify` from Milestone 2 onward (`--fail-on error`). Suppressing a self-finding follows the same per-site-with-justification rule as every other gate; bugs codekoll catches in itself get recorded (changelog/README material).
- **Performance is a gated feature:** once Milestone 9 lands, the load-test quick profile runs on every build; `baseline.json` is refreshed only deliberately, in its own PR, with the reason stated — a rule that blows the CPU/memory budget gets optimized or demoted, the baseline doesn't get quietly moved.
- Every quality gate (Checkstyle, PMD, SpotBugs, Error Prone, NullAway, ArchUnit, JaCoCo) fails the build; suppressions are per-site with a justification comment, never global.
- ARCHITECTURE.md is updated in the same PR as any structural change — JPMS `module-info` + the ArchUnit suite are the executable half of that document. The project skill (`.claude/skills/codekoll/SKILL.md`) is updated whenever commands, module layout, or the add-a-rule workflow change.
- No `com.sun.tools.javac.*` internal imports, ever — public compiler API only (enforced by both JPMS and ArchUnit), so the tool keeps working on future JDKs.
- New rules always follow the pattern: SPEC entry → fixtures → implementation (with `description`/`explanation`/`fix` metadata) → documented example class → generated docs row. No step skipped, in that order.
