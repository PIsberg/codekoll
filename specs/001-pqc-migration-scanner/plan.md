# Implementation Plan: PQC Migration Scanner & Static Guardrail

**Branch**: `001-pqc-migration-scanner` | **Date**: 2026-09-17 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-pqc-migration-scanner/spec.md`

## Summary

Add an eleventh codekoll rule pack, `pqc`, that inventories every quantum-vulnerable asymmetric
algorithm the JDK defines and warns without breaking builds. Three rules split by severity and role:
`CK-PQC-KEY-EXCHANGE` (WARNING), `CK-PQC-SIGNATURE` (WARNING), `CK-PQC-KEY-MATERIAL` (INFO). They
match compile-time-constant algorithm names at JCA/JCE request sites, parameter specifications, TLS
configuration and XML signature methods.

"All of Java's vulnerable algorithms" is enforced, not asserted. The catalog comes from the JDK
provider registry (96 vulnerable and 26 post-quantum service entries on JDK 26, plus aliases and
OIDs), and a completeness test fails the build when the running JDK exposes a name the catalog does
not classify. Guidance is advice only. Whether it points to the built-in ML-KEM/ML-DSA or to Bouncy
Castle PQC depends on the project's target release, found by looking up `NamedParameterSpec.ML_KEM_768`
in the compiler's symbol table. That needs no SPI change (research R5).

## Technical Context

**Language/Version**: Java 25 (`maven.compiler.release` 25); CI on Temurin 25, ubuntu-latest

**Primary Dependencies**: JDK compiler API (`com.sun.source.*` via `JavacTask`, already used); no new
runtime dependency. Bouncy Castle is named in guidance text only.

**Storage**: N/A

**Testing**: JUnit via `RuleTestHarness.assertFixture` (in-memory compile at release 25, `// ::`
markers); `ExampleVerificationTest` in `codekoll-examples`; `ArchitectureTest` (ArchUnit); new catalog
completeness test (contracts/catalog-completeness.md)

**Target Platform**: any JVM 25+ running the codekoll CLI; analyzed projects may target any release

**Project Type**: CLI static analyzer, Maven multi-module, JPMS

**Performance Goals**: SC-005, at most 5% more analysis time on the load-test corpus. Each rule is a
full traversal (`AbstractRule.scan`), so 3 rules added to 110 is the budget to hold; each visitor
filters on method name before resolving types.

**Constraints**: precision policy (SPEC.md section 8): constant names only, method-local, every
exemption has a negative fixture; severity fixed per rule (`RuleContext.report`); no change to
`Rule` SPI; source is never modified (FR-015)

**Scale/Scope**: 3 rules; catalog of 8 provider request types plus TLS groups (10), schemes (18),
suites (31) and XML DSig methods (33 constants) on JDK 26

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` is the unfilled template: no principles are ratified, so there is
no constitution to gate against. The plan is checked instead against the gates this repository
enforces in CI and its own SPEC.md, plus the owner's standing instructions. Running
`/speckit-constitution` would make these formal.

| Gate | Source | Pre-research | Post-design |
|---|---|---|---|
| Every rule has positive and negative fixtures; every exemption is a test | SPEC.md section 8, 9 | planned | PASS: exemptions enumerated in contracts/rules.md "Not reported" |
| Every rule has an example pair that fires / stays silent, class named from the id | `ExampleVerificationTest` | planned | PASS: `PqcKeyExchangeExample`, `PqcSignatureExample`, `PqcKeyMaterialExample`; `fixed()` uses JDK 25 ML-KEM/ML-DSA, no classpath needed |
| Metadata non-blank, explanation > 40 chars | `ArchitectureTest` | planned | PASS: content fixed in contracts/rules.md |
| Rules do not depend on report module or `com.sun.tools.javac` | `ArchitectureTest` | planned | PASS: uses `com.sun.source` and `javax.lang.model` only |
| Severity equals rule default | `ExampleVerificationTest:129` | forced the 3-rule split | PASS |
| Self-check `--fail-on error` on own sources stays green | CI `selfcheck` | WARNING/INFO cannot fail it | PASS: catalog stores names in collections, not at request sites, so the pack reports nothing on itself |
| Load-test regression gate (15% CPU, 20% heap) | CI `loadtest` | 3 traversals | PASS by design (SC-005 5%); measured at implementation, baseline updated only if findings change |
| Checkstyle, PMD, SpotBugs, JaCoCo | CI `quality`, `coverage` | apply unchanged | PASS: no suppressions planned |
| Behaviour change ships with a test; failing test first | owner instructions | planned | PASS: completeness test deliberately broken once (quickstart step 1) |
| Docs made wrong by the change are updated in the same change | owner instructions | counts in 8 files | PASS: list in research R8 |
| Out-of-scope items become issues when the PR opens | owner instructions | 8 items | PASS: research "Follow-ups" |

No violations; Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-pqc-migration-scanner/
├── plan.md                         # this file
├── research.md                     # Phase 0: decisions R1-R9, follow-ups, JDK 26 catalog appendix
├── data-model.md                   # Phase 1: RequestType, Family, Role, Classification, CatalogEntry
├── quickstart.md                   # Phase 1: end-to-end verification steps
├── contracts/
│   ├── rules.md                    # pack id, rule ids, severities, message formats, exemptions
│   └── catalog-completeness.md     # FR-016 test contract
├── checklists/requirements.md
└── tasks.md                        # Phase 2 (/speckit-tasks), not created here
```

### Source Code (repository root)

```text
codekoll-api/src/main/java/io/codekoll/api/
└── RulePack.java                               # + PQC (last constant)

codekoll-rules/src/main/java/
├── module-info.java                            # + provides ... Pqc*Rule (3)
└── io/codekoll/rules/
    ├── support/
    │   └── SourceKinds.java                    # new: test-source path check (research R6)
    └── pqc/                                    # new package
        ├── package-info.java
        ├── PqcCatalog.java                     # immutable classification table (data-model)
        ├── PqcSites.java                       # request-site matcher (research R4 table)
        ├── PqcGuidance.java                    # built-in probe + message text (contracts/rules.md)
        ├── PqcKeyExchangeRule.java             # CK-PQC-KEY-EXCHANGE, WARNING
        ├── PqcSignatureRule.java               # CK-PQC-SIGNATURE, WARNING
        └── PqcKeyMaterialRule.java             # CK-PQC-KEY-MATERIAL, INFO
codekoll-rules/src/main/resources/META-INF/services/io.codekoll.api.Rule   # + 3 lines

codekoll-rules/src/test/java/io/codekoll/rules/
├── support/SourceKindsTest.java
└── pqc/
    ├── PqcCatalogTest.java                     # no duplicate keys, alias/OID lookup, NotAsymmetric prefixes
    ├── PqcCatalogCompletenessTest.java         # FR-016 against the running JDK
    ├── PqcKeyExchangeRuleTest.java             # fixtures incl. hybrid, TLS, property, release variants
    ├── PqcSignatureRuleTest.java               # fixtures incl. XML DSig, schemes
    └── PqcKeyMaterialRuleTest.java             # fixtures incl. param specs, dedupe

codekoll-examples/src/main/java/examples/pqc/
├── PqcKeyExchangeExample.java
├── PqcSignatureExample.java
└── PqcKeyMaterialExample.java

Docs updated (FR-013, research R8): README.md, SPEC.md (+ section 6.11), PLAN.md, docs/RULES.md
(regenerated via --catalog), docs/CLI-PLAN.md, docs/CLI-SPEC.md, .claude/skills/codekoll/SKILL.md;
codekoll-load-test/baseline.json only if measured findings change.
```

**Structure Decision**: New package inside the existing `codekoll-rules` module, which is where every
built-in pack lives and what ServiceLoader discovery expects. No new Maven module: a separate module
would need its own JPMS wiring, CI jobs and fat-jar inclusion for three rules.

## Release-aware testing note

`RuleTestHarness` always compiles at release 25, so the "not built in" guidance variant (release < 24)
cannot be driven through `assertFixture`. `PqcGuidance` takes the probe result as an input so its two
message variants are unit-tested directly; one harness-level test for the older-release path needs a
harness overload accepting a release, which is a small additive change to
`codekoll-engine/.../testing/RuleTestHarness.java` and is included in this feature.

## Delivery

One PR from `001-pqc-migration-scanner` to `main`. The docs, registration lists and examples share
files, so the work is not split into separate PRs. The commit order puts the failing tests first: catalog + completeness test,
then each rule with its fixtures, then examples, then docs. Before handing over: open the 8 follow-up
issues from research.md and link them from the PR body, then follow CI to green.

## Complexity Tracking

No constitution or repository-gate violations to justify.