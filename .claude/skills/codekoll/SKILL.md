---
name: codekoll
description: Use when working on the codekoll Java static analyzer — adding or modifying rules, working on the engine/reporters/examples, running builds and quality gates, or updating SPEC.md/PLAN.md/ARCHITECTURE.md. Covers the add-a-rule workflow, module boundaries, and project invariants.
---

# Codekoll development skill

Codekoll is a static analysis tool for Java source (written in **Java 25**) that finds bugs which compile cleanly but fail at runtime. It is built on the JDK Compiler Tree API (`com.sun.source.*` via `JavacTask`), **never** JavaParser and **never** `com.sun.tools.javac.*` internals.

Authoritative documents — read before non-trivial changes, keep in sync with your change:
- `SPEC.md` — architecture, the 106-rule catalog (ten packs) with detection algorithms and exemptions.
- `README.md` — the public pitch (prior-art honesty + differentiators); its rule tables become generated in Milestone 10.
- `PLAN.md` — milestones, wave ordering, working agreements.
- `ARCHITECTURE.md` — module graph, engine internals, extension guides (exists from Milestone 1).

## Module map (Maven ≙ JPMS)

| Module | JPMS name | Contents | May depend on |
|---|---|---|---|
| `codekoll-api` | `io.codekoll.api` | `Rule` SPI, `Finding`, `RuleId`, `RulePack`, `Severity` | nothing |
| `codekoll-engine` | `io.codekoll.engine` | `JavacTask` driver, combined-traversal dispatcher, suppression, ServiceLoader registry | api, `jdk.compiler` |
| `codekoll-rules` | `io.codekoll.rules` | All rule packs, one package per pack; `AbstractRule` helpers; exports nothing | api, engine helpers |
| `codekoll-report` | `io.codekoll.report` | console/JSON/SARIF reporters | api only — **never** `jdk.compiler` |
| `codekoll-workspace` | `io.codekoll.workspace` | target-repo discovery (repo root, build system, source units, per-unit language level, hermetic classpath) **and** configuration: hand-rolled `codekoll.toml` reader, merging, provenance, §14 untrusted-repo limits | `java.xml` only — **never** api, engine or `jdk.compiler` |
| `codekoll-cli` | `io.codekoll.cli` | picocli front-end, `codekoll.toml` config, wiring; fat-jar entry | everything above |
| `codekoll-examples` | *(none, intentionally)* | one documented buggy/fixed example class per rule + E2E verification tests | the built analyzer (test scope) |
| `codekoll-load-test` | *(none, intentionally)* | perf harness: corpora, CPU/heap measurement, `baseline.json` regression gate, per-version chart images (`docs/perf/`) | the built analyzer (forked JVM) |

## Hard invariants (enforced by JPMS + ArchUnit — do not fight them)

- No `com.sun.tools.javac.*` imports anywhere. Public `com.sun.source.*` / `javax.lang.model.*` only.
- Rules never touch reporters; only the engine creates a `JavacTask`; only `cli` calls `System.exit` or writes to `System.out`.
- Every `Rule` is registered in `module-info` `provides`, has a `RulePack`, and has non-empty `description()`, `explanation()`, `fix()`.
- Rules are method-local (a few are class-local where SPEC says so, e.g. CK-PROXY-SELF-INVOKE): no interprocedural analysis in v1. If a detection idea needs cross-method flow, it goes to SPEC §11 Future Work instead.
- Precision policy (SPEC §8): prefer false negatives over false positives. Heuristic rules are INFO severity. Every exemption is contract + has a negative fixture.
- Quality gates all fail the build: Checkstyle, PMD, SpotBugs(+findsecbugs), Error Prone, NullAway (JSpecify `@NullMarked` everywhere), ArchUnit, JaCoCo (≥90% line / ≥85% branch). Suppressions are per-site with a justification comment, never global.

## Commands

```bash
mvn verify                        # full build: tests + ALL quality gates + dogfooding selfcheck
                                  # (codekoll runs on its own sources, --fail-on error)
mvn -pl codekoll-rules test       # fast loop while developing a rule
mvn -pl codekoll-examples verify  # example verification suite (all rules fire, fixed variants silent)
java -jar codekoll-cli/target/codekoll.jar --help
java -jar codekoll-cli/target/codekoll.jar codekoll-examples/src/main/java   # the 30-second demo
java -jar codekoll-cli/target/codekoll.jar --explain CK-THREAD-RUN           # rule docs from metadata
java -jar codekoll-cli/target/codekoll.jar .                                 # discover the repo, then analyze it
java -jar codekoll-cli/target/codekoll.jar --print-workspace .               # what discovery decided, before trusting it
java -jar codekoll-cli/target/codekoll.jar --print-config .                  # every effective setting + the file:line it came from
mvn -pl codekoll-load-test verify -Pquick    # perf quick profile vs baseline.json (also in CI per build)
mvn -pl codekoll-load-test verify -Pfull     # full perf suite + chart regeneration (nightly/release)
```

Dogfooding note: the `selfcheck` gate passes `--resolve none` on purpose. Discovery's `--classpath` replaces javac's fat-jar fallback, which is where `jspecify` comes from when codekoll analyzes itself; without it 55 of codekoll's own files stop type-checking and the gate quietly weakens. See the comment in `codekoll-cli/pom.xml`.

Perf note: `baseline.json` in `codekoll-load-test` is a gate like any other — never refresh it as a side effect; a deliberate baseline update goes in its own PR with the reason stated. Recording is opt-in and cannot happen by accident: `mvn -pl codekoll-load-test -am -Pquick verify -DskipTests -Dcodekoll.loadtest.record=true`, which rewrites only the current environment's entries and keeps the others. **Baselines are per environment** (`ci-linux`, `dev-windows`, …): performance numbers do not travel — the same analyzer measures 10.7 s / 63 MB here and 6.4 s / 103 MB on a GitHub runner — and comparing across that boundary failed three PRs that could not have changed performance. A run with no entry for its environment fails the gate and says how to record one; it never passes quietly. **Budgets differ by profile on purpose:** `quick` (every push, whatever runner is free) checks CPU for a doubling, because two CI runs of identical code differed by 2.1× and this machine varies 1.6× depending on what else is building; `full` keeps the tight ±15 % for a quiet machine. Heap keeps ±20 % everywhere: it moved 0.6 % between two CI runs and 11.2 % across three — noisier than first claimed, but a fraction of CPU's 2.1×, because it follows the work more than the host.

Never "fix" a red gate by disabling it or raising thresholds. If a tool lags JDK 25, pin a version and document the workaround in ARCHITECTURE.md.

## How to add a rule (the only accepted workflow, in this order)

1. **SPEC entry first**: add a row to the pack's table in SPEC §6 — ID (`CK-…`), severity (E/W/I), detection algorithm, exemptions in italics. Update the pack count and total in SPEC §4.
2. **Fixtures**: `positive/` files with `// :: finding-here` markers on offending lines; `negative/` files encoding every exemption (write these before the implementation).
3. **Implementation** in `codekoll-rules` under the pack's package: extend `AbstractRule`, subscribe to the minimal `Tree.Kind` set, fill in `description()`, `explanation()` (what is wrong + what happens at runtime), `fix()`. Register in `module-info` `provides`.
4. **Example class** in `codekoll-examples` (`examples.<pack>.<RuleName>Example`): class Javadoc with the three mandatory sections (**What is wrong / What happens at runtime / How to fix it**), a `buggy()` method with `// :: CK-…` markers, a `fixed()` method that must stay silent.
5. **Docs regenerate** from metadata (README catalog + examples README) — never hand-edit generated tables.
6. `mvn verify` green, including the registry-/pack-/doc-completeness tests in the examples suite. PLAN.md wave lists and counts updated if the catalog size changed.

Skipping a step breaks completeness tests by design — that's the point, don't route around them.

## Conventions

- Rule IDs: `CK-<SHORT-NAME>`, SCREAMING-KEBAB, stable forever once released (they appear in user suppressions).
- Rule messages: one sentence *why it's wrong*, one sentence *what to do*, concrete (`"…returns a new string; assign it: name = name.trim();"`).
- Shared detection machinery lives as reusable helpers, as data files where possible (known-pure methods, known-nullable methods, crypto blocklist), not copy-pasted logic: null-fact collector (CK-IMPOSSIBLE-COND), unrelated-types check (CK-GENERIC-MISMATCH), constant-concat taint shape (CK-SQL-CONCAT/CK-EXEC-CONCAT).
- `codekoll-examples` contains **intentional** bugs — external tools (SpotBugs/PMD/Error Prone) flagging them is expected; suppress per-file with `// intentional bug: demonstrates CK-…`, never module-wide.
- Commit/PR hygiene: SPEC/PLAN/ARCHITECTURE updates ship in the same PR as the change they describe; this SKILL.md updates when commands, module layout, or this workflow change.
