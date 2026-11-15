<!--
Sync Impact Report
- Version change: template (unversioned) -> 1.0.0
- Principles: all six newly defined (template placeholders replaced)
  I. Compiler Tree API Only
  II. Module Boundaries Hold
  III. Precision Over Recall
  IV. Every Rule Is Complete
  V. Quality Gates Fail the Build
  VI. Rules Are Method-Local in v1
- Added sections: Technology Constraints, Development Workflow, Governance
- Removed sections: none
- Templates:
  ✅ .specify/templates/plan-template.md (Constitution Check derives gates from this file; no edit needed)
  ✅ .specify/templates/spec-template.md (no constitution-specific sections; no edit needed)
  ✅ .specify/templates/tasks-template.md (generic task types cover rule, fixture and example tasks; no edit needed)
  ✅ .specify/templates/commands/ (directory absent in this install; nothing to check)
- Follow-up TODOs:
  - The codekoll skill (.claude/skills/codekoll/SKILL.md) states JaCoCo >= 90% line / 85% branch,
    while pom.xml enforces 50% / 40%. Principle V follows pom.xml; reconcile the skill text.
-->

# Codekoll Constitution

## Core Principles

### I. Compiler Tree API Only

Analysis MUST use the public JDK Compiler Tree API (`com.sun.source.*`, `javax.lang.model.*`)
through `JavacTask`. Code MUST NOT import `com.sun.tools.javac.*` internals and MUST NOT use
JavaParser.

Rationale: the public API is stable across JDK releases and gives full type attribution;
internals break on every JDK upgrade. Enforced by `ArchitectureTest`.
Source: `.claude/skills/codekoll/SKILL.md`.

### II. Module Boundaries Hold

Module dependencies MUST follow the module map:

- `codekoll-api` depends on nothing.
- `codekoll-report` depends on the api only and MUST NOT depend on `jdk.compiler`.
- `codekoll-workspace` depends on `java.xml` only, never on api, engine or `jdk.compiler`.
- Rules MUST NOT touch reporters.
- Only the engine creates a `JavacTask`.
- Only `codekoll-cli` calls `System.exit` or writes to `System.out`.

Rationale: keeps the compiler dependency contained in the engine and rule internals private.
Enforced by JPMS `module-info` and ArchUnit (CI job `archunit`).
Source: `.claude/skills/codekoll/SKILL.md` module map, `ARCHITECTURE.md`.

### III. Precision Over Recall

Rules MUST prefer false negatives to false positives. Heuristic rules MUST be INFO severity.
Every exemption is part of the rule's contract and MUST have a negative fixture proving the
rule stays silent.

Rationale: a finding a user learns to ignore costs more than a bug the tool misses.
Source: `SPEC.md` section 8, via `.claude/skills/codekoll/SKILL.md`.

### IV. Every Rule Is Complete

Every `Rule` MUST be registered in its module's `module-info` `provides` clause, belong to a
`RulePack`, and return non-empty `description()`, `explanation()` and `fix()`. Every rule MUST
have a documented buggy and fixed example class in `codekoll-examples`: the rule fires on the
buggy variant and stays silent on the fixed one.

Rationale: `--explain` and the generated docs are only as good as the rule metadata, and the
example suite is the primary end-to-end rule test.
Enforced by CI job `examples`. Source: `.claude/skills/codekoll/SKILL.md`,
`.github/workflows/ci.yml`.

### V. Quality Gates Fail the Build

Each of these gates MUST fail the build on violation: Checkstyle, PMD, SpotBugs with
find-sec-bugs, ArchUnit, JaCoCo at the minimums set in `pom.xml` (currently 50% line,
40% branch), the selfcheck (codekoll analyzing its own sources with `--fail-on error`), and
the perf quick profile against `baseline.json`. A suppression MUST be per site with a
justification comment, never global.

Rationale: codekoll's credibility depends on it passing its own analysis and on the gates
being the same locally and in CI. Source: `.github/workflows/ci.yml`, `pom.xml`.

### VI. Rules Are Method-Local in v1

Rules MUST analyze one method at a time, or one class where `SPEC.md` says so (for example
CK-PROXY-SELF-INVOKE). Interprocedural or whole-program analysis MUST NOT be added in v1; a
detection idea that needs cross-method flow goes to `SPEC.md` section 11 Future Work.

Rationale: method-local analysis keeps rules fast, predictable and precise (Principle III).
Source: `.claude/skills/codekoll/SKILL.md`.

## Technology Constraints

- Java 25 (`maven.compiler.release` 25), Maven multi-module build where each Maven module is a
  JPMS module (except `codekoll-examples` and `codekoll-load-test`, which intentionally have none).
- Runtime dependencies stay minimal: the fat jar bundles picocli and JSpecify; a new runtime
  dependency needs a stated reason in the plan.
- Production modules MUST NOT set the gate-skipping properties reserved for
  `codekoll-examples` and `codekoll-load-test`.

## Development Workflow

- `SPEC.md`, `README.md`, `PLAN.md` and `ARCHITECTURE.md` are authoritative and MUST be kept in
  sync with the change that affects them, in the same change.
- New rules follow the add-a-rule workflow in `.claude/skills/codekoll/SKILL.md`.
- `mvn verify` is the full local gate; CI runs the same gates per job.

## Governance

This constitution supersedes conflicting practice in the repository. Amendments are made by a
pull request that edits this file, states the reason, and updates dependent templates and docs
in the same PR. Versioning follows semantic versioning: MAJOR for removing or redefining a
principle, MINOR for adding a principle or materially expanding guidance, PATCH for wording.
Every plan's Constitution Check and every review verifies compliance with Principles I to VI;
a deviation MUST be justified in the plan's Complexity Tracking table.

**Version**: 1.0.0 | **Ratified**: 2026-11-15 | **Last Amended**: 2026-11-15
