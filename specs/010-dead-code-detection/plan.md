# Implementation Plan: Dead Code Detection

**Branch**: `010-dead-code-detection` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/010-dead-code-detection/spec.md`

## Summary

Four rules in a new `dead-code` pack (D5, D6), each one `TreePathScanner` over an attributed unit
like every existing rule: CK-DEAD-BRANCH (FR-002, WARNING), CK-UNREACHABLE (FR-001, WARNING),
CK-UNUSED-LOCAL (FR-003, INFO) and CK-UNUSED-PRIVATE (FR-003, FR-004, INFO). Registration through
the existing `ServiceLoader` registry gives them every output format, `--explain`, enable and
disable, severity overrides and `codekoll:off` (FR-006) with no reporter or CLI change.

Finding that shapes the design (D1): javac rejects every shape FR-001 names, and every loop whose
condition is constant false (JLS 14.22), and the engine never runs a rule on a file with a
compile error (`codekoll-engine/src/main/java/io/codekoll/engine/CompilationDriver.java: analyze`
moves it to `skipped`; `codekoll-cli/.../Main.java` prints `skipped (does not compile)`). Checked
2026-11-15 with javac: rejected are a statement after `return`, `while (false)`, `for (;false;)`,
`while (D)` with `static final boolean D = false`, and a statement after `while (true)` with no
break; accepted are `if (false)`, `if (D)`, `if (1 > 2)`, `false ? 1 : x` and a statement after
`if (true) { return 1; }`.

## Technical Context

**Language/Version**: Java 25 (`maven.compiler.release` 25)

**Primary Dependencies**: JDK Compiler Tree API only (`com.sun.source.*`, `javax.lang.model.*`); no new dependency

**Storage**: N/A

**Testing**: JUnit 5; rule fixtures through `codekoll-engine/src/main/java/io/codekoll/engine/testing/RuleTestHarness.java: assertFixture`; `codekoll-examples/src/test/java/examples/ExampleVerificationTest.java`; CLI tests through picocli `CommandLine`

**Target Platform**: JDK 25 CLI (fat jar)

**Project Type**: Static analyzer, Maven multi-module, one JPMS module per Maven module

**Performance Goals**: SC-003, at most 10% over the existing profile; checked by hand in [quickstart.md](quickstart.md) (T018), the CI quick profile only catches a doubling

**Constraints**: Principle III (silent when in doubt), Principle VI (method-local, CK-UNUSED-PRIVATE single-class per FR-011)

**Scale/Scope**: 4 rules; registry 113 to 117, SPEC catalog 117 to 121, packs 11 to 12

## Design

All new code is in package `io.codekoll.rules.deadcode` of `codekoll-rules`; every rule extends
`codekoll-rules/src/main/java/io/codekoll/rules/support/AbstractRule.java: AbstractRule` and
reports through `support/RuleContext.java: report(Tree, String)`, which anchors the finding at the
tree's start line. Messages follow the SKILL.md convention: why, then what to do, naming the
element (FR-005).

### Literal conditions (shared by CK-DEAD-BRANCH and CK-UNREACHABLE)

`LiteralConditions.valueOf(ExpressionTree)` returns `TRUE`, `FALSE` or `null`. It evaluates only:
boolean literals, `!`, `&&`, `||`, `^`, `==` and `!=` on booleans, and `==`, `!=`, `<`, `<=`, `>`,
`>=` between int, long or char literals (compared as long) or float and double literals (compared
as double), with parentheses stripped by `support/NullFacts.java: unwrap`. Any other node
(identifier, field access, call, string, cast) gives `null`, which means no finding: that is the
constant-flag exemption of FR-002 and US1 scenario 4 (D2).

### CK-DEAD-BRANCH (FR-002)

Fires on an `if` whose condition is `FALSE` (anchor: the then-statement), an `if` whose condition
is `TRUE` and that has an else (anchor: the else-statement), and a `?:` with a literal condition
(anchor: the dead arm). Never fires on loops (javac rejects them), `do { } while (false)` (the body
runs once), switches, or empty methods.

### CK-UNREACHABLE (FR-001, D1)

In a block or a switch case's statement list, an `if` without else whose condition is `TRUE` and
whose then-statement cannot complete normally (it is, or is a block whose last statement is,
recursively, a `return`, `throw`, `break`, `continue` or `yield`) makes the next statement of that
list unreachable; the finding anchors there. A named-constant condition is silent (D2).
`SwitchFallthroughRule`'s private `endsWithJump` already decides exactly that; it moves unchanged
to `support/Jumps.java: endsWithJump(List<? extends StatementTree>)`, called by both rules
(`List.of(thenStatement)` for an unbraced branch).

### Reads (shared by CK-UNUSED-LOCAL and CK-UNUSED-PRIVATE)

`Reads.in(CompilationUnitTree, Trees)` returns every element referenced anywhere in the unit
through an identifier, a field access, a method call's method select or a method reference
(`Trees.getElement` on the path), except as the variable of a plain `=` assignment. Compound
assignments and `++`/`--` count as reads (Principle III). A rule calls it once per unit, from
`visitCompilationUnit` of the scanner `scanner()` returns per unit.

### CK-UNUSED-LOCAL (FR-003)

Fires on a `VariableTree` whose element kind is `LOCAL_VARIABLE` and that `Reads.in` does not hold,
anchored at the declaration; the message says "never used" or "assigned but never read".
Parameters, lambda and catch parameters, try-with-resources variables and pattern bindings have
other element kinds and are never considered. Exempt, each with a negative fixture (Principle
III): enhanced-for variables, the unnamed variable `_`, a local carrying any annotation, and a name
that is or starts with `ignored` or `unused`.

### CK-UNUSED-PRIVATE (FR-003, FR-004, FR-009, FR-011)

Scope is the compilation unit: private access is shared by a top-level class and every type nested
in it (JLS 6.6.1), so this is the single-class scope FR-011 writes into SPEC.md. Considers private
methods and private fields only: not constructors, not nested types (D4), never a non-private
member (FR-009). Fires when `Reads.in` does not hold the element, anchored at the declaration; a
field assigned somewhere is "written but never read". Exempt, each with a negative fixture:

1. Referenced by a method reference or from a lambda body (FR-004); falls out of `Reads.in`.
2. Any annotation on the member (FR-004, D3).
3. A field of a type, or of a type enclosing it, that carries an annotation outside `java.lang`
   and `org.jspecify.annotations` (D3: Lombok accessors, JPA entities).
4. Serialization contract (FR-004), by name and parameter types whether or not the class is
   `Serializable`: fields `serialVersionUID`, `serialPersistentFields`; methods
   `writeObject(java.io.ObjectOutputStream)`, `readObject(java.io.ObjectInputStream)`,
   `readObjectNoData()`, `writeReplace()`, `readResolve()`.
5. Reflection (spec edge case): the member's name equals a string literal anywhere in the unit.
6. A method with the name of an annotated method of the same type (JUnit's `@MethodSource` with no
   value names the factory after the test).
7. A field backing a record component (`TypeElement.getRecordComponents()`).

### Wiring and existing behaviour

- Registration (T017): `codekoll-rules/src/main/java/module-info.java` `provides` and
  `codekoll-rules/src/main/resources/META-INF/services/io.codekoll.api.Rule`, which
  `codekoll-cli/src/test/java/io/codekoll/cli/RegistryConsistencyTest.java` compares.
  `Settings` reads pack names from `RulePack`, so `disable-packs = ["dead-code"]` needs no code.
- Generated sources are already excluded by
  `codekoll-workspace/src/main/java/io/codekoll/workspace/FileSelector.java` (`GENERATED_DIRS`,
  `looksGenerated`); test sources are analyzed as by every rule that ignores `SourceKind`. No task.
- `ExampleVerificationTest.findingsMatchMarkersExactly` runs every registered rule on every
  example, so the INFO rules may fire on other rules' examples once registered (T017). A false
  positive is fixed in the rule with a negative fixture (Principle III); a true one gets a per-line
  `codekoll:off <ID>` with its reason (`engine/SuppressionFilter.java: isSuppressed` matches the id
  anywhere after the marker, so it can follow an existing `// :: CK-...` marker), never a file or
  module suppression (Principle V).
- FR-010: all four rules default to WARNING or INFO, so the selfcheck at `--fail-on error` cannot
  fail on them; SC-002's review of their findings on codekoll's own sources is in quickstart.md.

## Constitution Check

| Rule | Tasks | How |
|---|---|---|
| I Compiler Tree API only | T004, T007, T011, T014 | Only `com.sun.source.*`, `javax.lang.model.*`; existing `ArchitectureTest` |
| II Module boundaries | T001, T004, T007, T011, T014 | Enum constant in api; rules stay in `codekoll-rules`, report through `RuleContext` |
| III Heuristic rules are INFO | T006, T013 | Tests assert INFO for both unused rules (FR-007) |
| III Every exemption has a negative fixture | T003, T006, T010, T013, T018 | One negative per exemption listed in Design; T018 adds one per false positive SC-002's review finds |
| IV Registered, in a pack, metadata non-empty | T001, T017; T003, T006, T010, T013 | `provides` + services file; tests assert pack and non-blank metadata |
| IV Buggy and fixed example per rule | T015, T017 | Four example classes, verified by `ExampleVerificationTest` |
| V Gates fail the build, per-site suppressions | T017 | Collateral example findings suppressed per line with a reason |
| VI Method-local, or one class where SPEC says so | T008, T014 | SPEC lists CK-UNUSED-PRIVATE as single-class; scope is one unit |
| Workflow: SPEC, README, PLAN, ARCHITECTURE in sync | T008, T017 | ARCHITECTURE.md names no packs or counts: no edit |
| Workflow: add-a-rule order | T008, T003/T006/T010/T013, T015, T017 | SPEC first, fixtures before code, example, regenerated catalog |

Not applicable: Java 25 and the module layout (unchanged); new runtime dependency (none);
gate-skipping properties (no `pom.xml` change); Governance (no amendment).

## Project Structure

### Documentation (this feature)

```text
specs/010-dead-code-detection/
├── spec.md
├── plan.md
├── quickstart.md        # SC-002 and SC-003, checked by hand
└── tasks.md
```

### Source Code (repository root)

```text
codekoll-api/src/main/java/io/codekoll/api/RulePack.java          # + DEAD_CODE
codekoll-rules/src/main/java/io/codekoll/rules/deadcode/
├── package-info.java
├── LiteralConditions.java
├── Reads.java
├── DeadBranchRule.java
├── UnreachableRule.java
├── UnusedLocalRule.java
└── UnusedPrivateRule.java
codekoll-rules/src/main/java/io/codekoll/rules/support/Jumps.java  # endsWithJump, moved
codekoll-rules/src/main/java/io/codekoll/rules/correctness/SwitchFallthroughRule.java  # calls Jumps
codekoll-rules/src/main/java/module-info.java                      # provides
codekoll-rules/src/main/resources/META-INF/services/io.codekoll.api.Rule
codekoll-rules/src/test/java/io/codekoll/rules/deadcode/*RuleTest.java
codekoll-examples/src/main/java/examples/deadcode/*Example.java
codekoll-cli/src/test/java/io/codekoll/cli/DeadCodeCliTest.java
SPEC.md, README.md, PLAN.md, docs/RULES.md, .claude/skills/codekoll/SKILL.md
```

**Structure Decision**: one package per pack, as for the eleven existing packs.

## Complexity Tracking

None: no constitution deviation.

## Decisions

### D1: FR-001's shapes and constant-false loops do not compile (JLS 14.22). What does CK-UNREACHABLE report?

Answer (2026-11-15): A, as recommended

- **A (recommended): The compiling counterpart: the statement after an `if` with a literal-true condition whose branch cannot complete normally.**
  Keeps four rules and T009 to T011. Only literal conditions fire (D2), so `if (DEBUG) return;`
  stays silent. Plain `return; x++;` stays a skipped file, as today. Spec lines below record that
  javac owns those shapes.
  Spec: US1: "A developer scans their project and is told about statements and branches that can never execute: a statement after an unconditional return, throw, break or continue, a branch guarded by a condition that is always false, and a loop whose body can never be entered." becomes "A developer scans their project and is told about statements and branches that can never execute: a statement after an `if (true)` whose branch always returns, throws, breaks or continues, and a branch guarded by a condition that is always false. A statement after a plain return, throw, break or continue, and a loop whose condition is constant false, do not compile (JLS 14.22): codekoll lists such files as skipped, as it does every file that does not compile."
  Spec: US1 scenario 1: "1. **Given** a method with a statement after an unconditional `return`, **When** the project is scanned, **Then** a finding is reported at that statement naming it as unreachable." becomes "1. **Given** a method with a statement after `if (true) { return; }`, **When** the project is scanned, **Then** a finding is reported at that statement naming it as unreachable."
  Spec: Edge Cases: "- A statement after an infinite loop with no exit is reported; after a loop that can exit it is not." becomes "- A statement after an infinite loop with no exit does not compile (JLS 14.22): codekoll lists the file as skipped and no rule reports it."
  Spec: FR-001: "- **FR-001**: The scanner MUST report a statement that follows an unconditional return, throw, break or continue in the same block as unreachable code." becomes "- **FR-001**: The scanner MUST report, as unreachable code, the statement that follows an `if` whose condition is constant true and whose branch ends in a return, throw, break, continue or yield. A statement after a plain return, throw, break or continue is a compile error (JLS 14.22), which codekoll reports by listing the file as skipped."
  Spec: FR-002: "- **FR-002**: The scanner MUST report a branch or loop body guarded by a condition that is constant-false as dead code, except for the documented constant-flag idiom (User Story 1, scenario 4)." becomes "- **FR-002**: The scanner MUST report an `if` branch or a `?:` arm guarded by a condition that is constant-false as dead code, except for the documented constant-flag idiom (User Story 1, scenario 4). A loop whose condition is constant false is a compile error (JLS 14.22) and no rule reports it."
- **B: Drop CK-UNREACHABLE: javac already enforces FR-001.**
  Removes T009 to T011 and `UnreachableExample` from T015; three rules, so T017's counts become
  116 registered and 120 specified, and T016 explains three IDs. FR-001 then has no rule and needs
  `No test: compile error, existing skipped-file report` in tasks.md.
  Spec: US1: "A developer scans their project and is told about statements and branches that can never execute: a statement after an unconditional return, throw, break or continue, a branch guarded by a condition that is always false, and a loop whose body can never be entered." becomes "A developer scans their project and is told about branches that can never execute: a branch guarded by a condition that is always false. A statement after a return, throw, break or continue, and a loop whose condition is constant false, do not compile (JLS 14.22): codekoll lists such files as skipped, as it does every file that does not compile."
  Spec: US1 scenario 1: "1. **Given** a method with a statement after an unconditional `return`, **When** the project is scanned, **Then** a finding is reported at that statement naming it as unreachable." becomes "1. **Given** a method with a statement after an unconditional `return`, **When** the project is scanned, **Then** the file is listed as skipped because it does not compile."
  Spec: Edge Cases: "- A statement after an infinite loop with no exit is reported; after a loop that can exit it is not." becomes "- A statement after an infinite loop with no exit does not compile (JLS 14.22): codekoll lists the file as skipped and no rule reports it."
  Spec: FR-001: "- **FR-001**: The scanner MUST report a statement that follows an unconditional return, throw, break or continue in the same block as unreachable code." becomes "- **FR-001**: A statement that follows an unconditional return, throw, break or continue is a compile error (JLS 14.22); codekoll MUST keep listing such files as skipped, and no rule reports it."
  Spec: FR-002: "- **FR-002**: The scanner MUST report a branch or loop body guarded by a condition that is constant-false as dead code, except for the documented constant-flag idiom (User Story 1, scenario 4)." becomes "- **FR-002**: The scanner MUST report an `if` branch or a `?:` arm guarded by a condition that is constant-false as dead code, except for the documented constant-flag idiom (User Story 1, scenario 4). A loop whose condition is constant false is a compile error (JLS 14.22) and no rule reports it."

### D2: Which constant conditions are the constant-flag idiom (FR-002, US1 scenario 4)?

Answer (2026-11-15): A, as recommended

- **A (recommended): Any condition that names a variable is the idiom; only literal-only conditions fire.**
  `if (false)`, `if (1 > 2)`, `if (!true)` fire; `if (DEBUG)`, `if (DEBUG && TRACE)`,
  `if (MAX < 0)` stay silent. `LiteralConditions` needs no symbol lookup. Negatives in T003 and
  T010 cover a bare flag, a negated flag and a compound of flags.
  Spec: US1 scenario 2: "2. **Given** a method with `if (false)` or a condition built only from constants that is always false, **When** scanned, **Then** the guarded block is reported as dead." becomes "2. **Given** a method with `if (false)` or a condition built only from literals that is always false (such as `1 > 2`), **When** scanned, **Then** the guarded block is reported as dead."
  Spec: US1 scenario 4: "4. **Given** a debug flag declared as a constant that guards a block on purpose (conditional-compilation idiom), **When** scanned, **Then** no finding is reported for that block." becomes "4. **Given** a condition that names a constant variable, such as a debug flag guarding a block on purpose (conditional-compilation idiom), **When** scanned, **Then** no finding is reported for that block."
- **B: Only a bare boolean constant or its negation is the idiom; other constant conditions fire.**
  `LiteralConditions` folds `static final` constants through `Trees.getElement` and
  `VariableElement.getConstantValue()`; `if (DEBUG && TRACE)` and `if (MAX < 0)` fire, which
  reports feature-flag combinations that are the same idiom in practice. T003 and T010 gain those
  positives and keep negatives for `if (DEBUG)` and `if (!DEBUG)`.
  Spec: US1 scenario 4: "4. **Given** a debug flag declared as a constant that guards a block on purpose (conditional-compilation idiom), **When** scanned, **Then** no finding is reported for that block." becomes "4. **Given** a single boolean constant or its negation guarding a block on purpose (`if (DEBUG)`, `if (!DEBUG)`, the conditional-compilation idiom), **When** scanned, **Then** no finding is reported for that block."

### D3: Which annotations mark framework use (FR-004)?

Answer (2026-11-15): A, as recommended

- **A (recommended): Any annotation on the member; for fields, also any annotation outside `java.lang` and JSpecify on the declaring or an enclosing type.**
  Covers Spring, CDI, Jackson and JUnit on members, and Lombok and JPA on the class, with no list
  to maintain. Cost: every private field of a `@Service` or `@Entity` class is silent; private
  methods there are still checked. Exemptions 2 and 3 in Design; negatives in T013.
  Spec: FR-004: "- **FR-004**: The scanner MUST NOT report a private member that is referenced by method reference or lambda, carries an annotation marking framework use, or is required by a serialization contract." becomes "- **FR-004**: The scanner MUST NOT report a private member that is referenced by method reference or lambda, carries any annotation, is a field of a type carrying an annotation outside `java.lang` and JSpecify, or is required by a serialization contract."
- **B: A listed set of framework packages (`javax`/`jakarta.inject`, `jakarta.persistence`, `org.springframework`, `com.fasterxml.jackson`, `lombok`, `org.junit`), on the member or, for Lombok and JPA, the type.**
  Adds a data file under `codekoll-rules/src/main/resources/` and its loader to T014, one negative
  per package in T013; an unlisted framework yields false positives until the list grows.
  Spec: FR-004: "- **FR-004**: The scanner MUST NOT report a private member that is referenced by method reference or lambda, carries an annotation marking framework use, or is required by a serialization contract." becomes "- **FR-004**: The scanner MUST NOT report a private member that is referenced by method reference or lambda, carries an annotation from a listed framework package (or, for a field, sits in a type carrying a Lombok or JPA annotation), or is required by a serialization contract."

### D4: US2 names private nested types, FR-003 does not. Are unused private nested types reported?

Answer (2026-11-15): A, as recommended

- **A (recommended): No; FR-003's list holds.**
  No extra work; US2's sentence is aligned with FR-003.
  Spec: US2: "A developer is told about private methods, private fields, private nested types, and local variables that are never used within the class that declares them, so they can delete them." becomes "A developer is told about private methods, private fields and local variables that are never used within the class that declares them, so they can delete them."
- **B: Yes, CK-UNUSED-PRIVATE also reports a private nested type no code in the unit names.**
  T013 gains a positive and negatives (annotated type, name in a string literal); T014 resolves type
  references in `Reads.in` (identifiers in type positions, `new`, `instanceof`, casts).
  Spec: FR-003: "- **FR-003**: The scanner MUST report private methods, private fields and local variables that are never used within their declaring class. Never-read-but-written counts as unused." becomes "- **FR-003**: The scanner MUST report private methods, private fields, private nested types and local variables that are never used within their declaring class. Never-read-but-written counts as unused."

### D5: The four rules need a pack, and `RulePack` is a public enum in `codekoll-api`. Which pack?

Answer (2026-11-15): A, as recommended

- **A (recommended): A new constant `RulePack.DEAD_CODE` (`dead-code`).**
  Additive API change (T001). `disable-packs = ["dead-code"]` silences the category in one setting
  (US3 scenario 3, T016). README and SPEC say twelve packs (T017; `RegistryConsistencyTest`
  already knows the word).
- **B: Put them in `correctness`.**
  No API change; T001 drops the enum constant, classes move to `io.codekoll.rules.correctness`
  and examples to `examples.correctness`; the category is disabled by listing four IDs in
  `rules.disable` (T016); T017 keeps eleven packs and raises `correctness` from 26 to 30.
  Spec: US3 scenario 3: "3. **Given** the dead-code rules are disabled through configuration, **When** scanned, **Then** none are reported." becomes "3. **Given** the four dead-code rule IDs are listed in the configuration's disable list, **When** scanned, **Then** none are reported."

### D6: Rule IDs are permanent once released (they appear in user suppressions). Which IDs?

Answer (2026-11-15): A, as recommended

- **A (recommended): CK-UNREACHABLE, CK-DEAD-BRANCH, CK-UNUSED-PRIVATE, CK-UNUSED-LOCAL.**
  Each names what it finds; classes and examples follow the
  `ExampleVerificationTest: exampleClassName` convention (`UnusedLocalExample`).
- **B: A shared prefix: CK-DEAD-STATEMENT, CK-DEAD-BRANCH, CK-DEAD-PRIVATE, CK-DEAD-LOCAL.**
  Groups them in sorted catalogs; class and example names in T002, T005, T009, T012 and T015
  change to match (`DeadStatementRule`, `DeadStatementExample`, ...).
