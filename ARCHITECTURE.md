# Codekoll — Architecture

How codekoll is built. Companion to [SPEC.md](SPEC.md) (what it detects) and
[PLAN.md](PLAN.md) (in what order). The ArchUnit suite
(`codekoll-cli/src/test/java/io/codekoll/cli/ArchitectureTest.java`) is the executable half
of this document — every constraint stated here is either enforced by JPMS at compile time
or by a named test.

## Module map

Maven modules ≙ JPMS modules (production), plus two intentionally non-modular helpers:

```
codekoll-api        io.codekoll.api      Rule SPI: Rule, RulePack, Finding, RuleId, Severity,
                                         FindingCollector. requires transitive jdk.compiler
                                         (the SPI exposes attributed trees).
codekoll-engine     io.codekoll.engine   CompilationDriver (JavacTask), SuppressionFilter,
                                         RuleRegistry (ServiceLoader), fixture harness
                                         (io.codekoll.engine.testing). uses io.codekoll.api.Rule.
codekoll-rules      io.codekoll.rules    Rule packs, one package per pack. Exports nothing;
                                         provides io.codekoll.api.Rule with <all rules>.
codekoll-report     io.codekoll.report   Console/JSON reporters (SARIF in M10).
                                         Depends on api only — never on jdk.compiler.
codekoll-workspace  io.codekoll.workspace  Target-repo discovery (M11): repo root, build system,
                                         source units, per-unit language level, hermetic
                                         classpath discovery. requires java.xml only — not
                                         jdk.compiler, not api, not the engine.
codekoll-cli        io.codekoll.cli      picocli front-end; shaded into codekoll.jar.
codekoll-examples   (non-modular)        One documented buggy/fixed example class per rule
                                         + the E2E verification suite.
codekoll-load-test  (non-modular)        Perf harness (M9): corpora, baseline gate, charts.
```

Why the split: rule discovery is a textbook ServiceLoader case (`provides`/`uses`), the
compiler dependency stays contained in the engine, and rule internals are genuinely private
because `io.codekoll.rules` exports nothing. `codekoll-examples` stays non-modular because it
represents typical user code — and its intentional bugs must not fight module tooling.

Note on the shaded jar: `maven-shade-plugin` flattens the module graph (module-info.class
excluded, services merged via `ServicesResourceTransformer`), so at *runtime* the fat jar is
classpath-based. JPMS still pays off at compile time; the ArchUnit tests re-check the same
boundaries on the classpath build.

A useful consequence: the fat jar bundles the analyzer's few dependencies, so when codekoll
analyzes *itself* with no `--classpath`, javac's default classpath (the fat jar) already
contains jspecify and picocli — the selfcheck needs no dependency wrangling.

That fallback is fragile in one specific way, and the dogfooding gate depends on it. Since M11
every run discovers a workspace, and `--resolve discover` assembles a `--classpath` from build
output found on disk. Passing *any* classpath replaces the default, so jspecify stops resolving
and 55 of codekoll's own files stop attributing. The `selfcheck` execution therefore passes
`--resolve none`; the general fix is `--resolve build` (CLI-PLAN Milestone 13).

## How the CLI wires the three worlds together

`codekoll-cli` is the only module that knows about workspace, engine and report at once:

1. `WorkspaceDiscovery.discover(paths)` returns a `Workspace`: repo root, build system, ordered
   `SourceUnit`s (files + release + classpath), and a diagnostic for every guess it made.
2. One `CompilationDriver` per unit, each with that unit's `--release` and classpath; the
   results are merged and sorted once, so output ordering does not depend on how discovery
   split the repository. (`AnalysisUnit` and `Attribution` in the engine are the shape M14's
   `analyzeUnits` will take — batching, timeouts and attribution counters belong there.)
3. Reporters receive a `PathRenderer`. Findings keep absolute paths internally; the CLI hands
   the reporter `Workspace::relativize`, or the identity renderer under `--absolute-paths`, so
   `codekoll-report` prints repo-relative paths while still depending on nothing but the api.
   SARIF needs this specifically: absolute build-agent paths annotate nothing on GitHub.

## Compilation pipeline

`CompilationDriver` drives the *system* compiler (`ToolProvider.getSystemJavaCompiler()`):

1. Collect `.java` files from CLI paths (skipping `module-info.java` — analyzed code is
   treated as plain sources, not a module graph).
2. One `JavacTask` per run, all files together, so cross-file symbols resolve.
   Options: `--release <n>`, `-proc:none` (no annotation processing), `-nowarn`.
3. `task.parse()` then `task.analyze()` — **attribution is the whole point**: after
   `analyze()`, `Trees.getTypeMirror`/`getElement` return resolved types, generics, and
   symbols. Most rules are impossible on a bare parse tree.
4. Per compilation unit: if javac reported an ERROR diagnostic for that unit, the file is
   recorded as *skipped* (with the first diagnostic) and not scanned — codekoll analyzes
   code that compiles; it does not guess about code that doesn't.
5. Every enabled rule scans every surviving unit. A rule that throws is caught, recorded in
   `AnalysisResult.ruleFailures`, and analysis continues (a crashing rule must never take
   down the run — the engine's contract).
6. Findings are sorted (file, line, column, rule id) for deterministic output.

Class output goes to a temp directory; codekoll never keeps generated classes.

### Boundaries

- **Public compiler API only**: `com.sun.source.*` and `javax.lang.model.*`.
  `com.sun.tools.javac.*` internals are banned — unexported by JPMS, rejected by Checkstyle
  (`IllegalImport`) and ArchUnit (`noJavacInternals`). This keeps codekoll working on future
  JDKs.
- Only the engine creates a `JavacTask` (ArchUnit: `onlyEngineTouchesJavacTask`).
- Rules never touch reporters (ArchUnit: `rulesDoNotDependOnReporters`).

## Rule SPI and lifecycle of a finding

`Rule` (in `io.codekoll.api`) is stateless; discovered via `ServiceLoader.load(Rule.class)`
by `RuleRegistry`, sorted by id for deterministic ordering, filtered by `--rules`/`--packs`.

Self-documentation contract: `description()` (one line), `explanation()` (what is wrong +
what happens at runtime), `fix()` (what to do). These are load-bearing: finding messages,
`--explain`, and the generated docs all come from them. ArchUnit rejects a rule with blank
metadata (`everyRuleHasCompleteMetadata`); the examples suite additionally proves every rule
fires on a documented example.

A finding's path: rule scanner calls `RuleContext.report(tree, message)` → position and
snippet resolved from the tree → `FindingCollector` (engine's `FindingSink`) applies
suppression (`// codekoll:off [CK-…]` on the finding's line) → collected, sorted, handed to
a `Reporter`.

`AbstractRule`/`RuleContext` (internal to `codekoll-rules`) carry the shared helpers:
`report`, `typeOf`, `isSubtypeOf`, `qualifiedNameOf`. They grow with the catalog (null-fact
collector, unrelated-types check, … arrive with the rules that need them, as reusable
utilities per PLAN).

### Current dispatch model — and the planned combined dispatcher

Today each rule runs its own `TreePathScanner` pass per unit (fine at 3 rules). SPEC §10
requires a **combined single traversal** with per-rule `Tree.Kind` subscriptions before the
catalog scales (M6 profiles it at ~85 rules). The seam is `AbstractRule.scanner()`: the
engine-side change swaps N full walks for one dispatching walk without touching rule code.

## Testing

- **Fixture harness** (`io.codekoll.engine.testing.RuleTestHarness`): compiles a source
  string in-memory (`StringSource`), runs one rule, and asserts findings appear at exactly
  the `// ::`-marked lines. Positive fixtures mark lines; negative fixtures (exemptions)
  must stay silent. This is the primary rule-test mechanism.
- **Examples verification** (`codekoll-examples`): runs the real driver over the examples
  sources and enforces: findings ≙ markers exactly, every rule has a firing example, naming
  convention (`CK-EMPTY-CATCH` → `EmptyCatchExample.java`), and every example documents
  *What is wrong / What happens at runtime / How to fix*.
- **ArchUnit** (`codekoll-cli`, plus `codekoll-workspace` for its own boundaries): the constraints
  above. Discovery's rules live in the module they constrain so they travel with it.
- **Dogfooding**: codekoll runs on its own production sources (`--fail-on error`) — see
  PLAN's standing dogfooding gate.

## Quality gates (all fail the build)

Checkstyle 13.x (lean correctness config, `config/checkstyle.xml`), PMD 7.x
(`bestpractices` + `errorprone`, `config/pmd-ruleset.xml`), SpotBugs 4.10+ with
find-sec-bugs (`config/spotbugs-exclude.xml` — per-site exclusions with justification only),
JaCoCo (thresholds in the parent POM; bootstrap values ratchet up per PLAN), ArchUnit.
Error Prone + NullAway wiring is tracked in PLAN M0 (pending JDK-26-compatible releases —
the toolchain note below).

`codekoll-examples` and `codekoll-load-test` skip the external gates (`*.skip` properties in
their POMs): the examples are intentional bugs, the harness is never shipped. Production
modules must never set those properties.

## Toolchain notes (JDK 25/26)

Verified working on the build JDK (currently JDK 26; `--release 25` for production code):
Checkstyle 13.8.0, PMD 7.26.0, SpotBugs 4.10.3.0 — earlier versions fail on the class-file
version, which is why they are pinned (PLAN M0: "pin versions that work, document here").
JaCoCo's agent must not instrument JDK classes loaded by the in-process javac — the agent
excludes `com.sun.*`/`jdk.*`/`sun.*`/`java.*`/`javax.*` (parent POM).

## Design decisions & rejected alternatives

| Decision | Rejected | Why |
|---|---|---|
| javac Compiler Tree API | JavaParser | Day-one new-syntax support; exact attribution (types/generics) because it *is* the compiler. JavaParser's symbol solver is weakest exactly where the hard rules live. |
| Source analysis | Bytecode (SpotBugs-style) | Zero build integration — point at a source tree; no compiled artifacts needed. Trade-off: analyzed code must compile. |
| ServiceLoader + JPMS `provides` | Hand-rolled registry | Third-party rule packs are just jars on `--rule-path`; no registry edits. |
| Per-rule passes now, combined dispatcher at scale | Combined-first | 3 rules don't earn the complexity; the seam is designed so the swap is engine-only. |
| Records + defensive copies in API | Mutable DTOs | `Finding`/`AnalysisResult` are values; `AnalysisResult` defensively copies (codekoll's own CK-RECORD-MUTABLE-COMPONENT rule, before it exists). |
