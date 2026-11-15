# Feature Specification: Dead Code Detection

**Feature Branch**: `010-dead-code-detection`

**Created**: 2026-11-15

**Status**: Draft

**Input**: User description: "implement so codekoll can identify and report dead code"

## Clarifications

### Session 2026-11-15

- Q: Are unused private members and locals (User Story 2) in this feature or deferred? → A: In this feature, with an amendment to the rule specification listing them as allowed single-class rules (Constitution Principle VI). They are heuristic, so INFO severity (Principle III).
- Q: Are unreachable-statement findings WARNING or INFO? → A: WARNING. The constant-false-branch findings, equally certain, are also WARNING.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find code that can never run inside a method (Priority: P1)

A developer scans their project and is told about statements and branches that can never execute: a statement after an `if (true)` whose branch always returns, throws, breaks or continues, and a branch guarded by a condition that is always false. A statement after a plain return, throw, break or continue, and a loop whose condition is constant false, do not compile (JLS 14.22): codekoll lists such files as skipped, as it does every file that does not compile.

**Why this priority**: These are certain defects with no false-positive risk, and they fit the existing one-method-at-a-time analysis.

**Independent Test**: Scan a sample class holding one such case per kind and confirm each is reported with file, line and an explanation; scan the corrected class and confirm silence.

**Acceptance Scenarios**:

1. **Given** a method with a statement after `if (true) { return; }`, **When** the project is scanned, **Then** a finding is reported at that statement naming it as unreachable.
2. **Given** a method with `if (false)` or a condition built only from literals that is always false (such as `1 > 2`), **When** scanned, **Then** the guarded block is reported as dead.
3. **Given** the same methods corrected so every statement is reachable, **When** scanned, **Then** no dead-code finding is reported.
4. **Given** a condition that names a constant variable, such as a debug flag guarding a block on purpose (conditional-compilation idiom), **When** scanned, **Then** no finding is reported for that block.

---

### User Story 2 - Find unused private members and locals (Priority: P2)

A developer is told about private methods, private fields and local variables that are never used within the class that declares them, so they can delete them.

**Why this priority**: Most common everyday dead code, but needs a per-class view and care about reflection and annotation-driven use.

**Independent Test**: Scan a class with an unused private method, an unused private field and an unused local, and confirm each is reported. Scan one where each is used and confirm silence.

**Acceptance Scenarios**:

1. **Given** a private method never called in its class, **When** scanned, **Then** a finding names the method and its line.
2. **Given** a private field that is only written and never read, **When** scanned, **Then** a finding reports it as never read.
3. **Given** a private member carrying an annotation that marks framework or serialization use, or one required by a serialization contract, **When** scanned, **Then** no finding is reported.
4. **Given** a private method referenced only from a method reference or a lambda, **When** scanned, **Then** no finding is reported.

---

### User Story 3 - Control and read the results (Priority: P3)

A developer can see dead-code findings in every existing output format alongside other findings, silence a single finding or the whole category, and ask `--explain` why a finding was raised and how to fix it.

**Why this priority**: Makes the feature usable in CI without noise.

**Independent Test**: Run a scan with the category disabled, enabled, and with one finding suppressed, in each output format.

**Acceptance Scenarios**:

1. **Given** a project with dead code, **When** scanned in each supported output format, **Then** the findings appear in each with rule ID, location and message.
2. **Given** a dead-code rule ID, **When** the user asks for its explanation, **Then** a description, explanation and fix are shown.
3. **Given** the dead-code rules are disabled through configuration, **When** scanned, **Then** none are reported.

---

### Edge Cases

- Generated sources and test sources: findings in generated code are not reported; test sources follow the existing test-source behaviour.
- Code reachable only through reflection, dependency injection, or serialization hooks must not be reported.
- Empty or stub methods and `default`-only `switch` branches are not dead code.
- A statement after an infinite loop with no exit does not compile (JLS 14.22): codekoll lists the file as skipped and no rule reports it.
- Non-private members (public, protected, package-private) are not reported in this feature.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The scanner MUST report, as unreachable code, the statement that follows an `if` whose condition is constant true and whose branch ends in a return, throw, break, continue or yield. A statement after a plain return, throw, break or continue is a compile error (JLS 14.22), which codekoll reports by listing the file as skipped.
- **FR-002**: The scanner MUST report an `if` branch or a `?:` arm guarded by a condition that is constant-false as dead code, except for the documented constant-flag idiom (User Story 1, scenario 4). A loop whose condition is constant false is a compile error (JLS 14.22) and no rule reports it.
- **FR-003**: The scanner MUST report private methods, private fields and local variables that are never used within their declaring class. Never-read-but-written counts as unused.
- **FR-004**: The scanner MUST NOT report a private member that is referenced by method reference or lambda, carries any annotation, is a field of a type carrying an annotation outside `java.lang` and JSpecify, or is required by a serialization contract.
- **FR-005**: Every dead-code finding MUST carry rule ID, file, line, a message naming the dead element, and an explanation and fix reachable through `--explain`.
- **FR-006**: Dead-code findings MUST appear in all existing output formats and be controllable by the existing enable/disable, severity and suppression mechanisms.
- **FR-007**: Findings where certainty depends on heuristics (the unused private member and unused local rules) MUST be INFO severity (Constitution Principle III). Unreachable-statement and constant-false-branch findings MUST be WARNING severity.
- **FR-011**: The project's rule specification MUST be amended in the same change to list unused private members and unused locals as allowed single-class rules.
- **FR-008**: Each rule MUST have a buggy and a fixed documented example, and a negative case for every exemption in FR-002 and FR-004.
- **FR-009**: The scanner MUST NOT report public, protected or package-private members, nor perform analysis across classes (see Assumptions).
- **FR-010**: Scanning the project's own sources with the new rules MUST produce no ERROR findings.

### Key Entities

- **Dead-code finding**: A reported element (statement, branch, member or local) with location, kind (unreachable, constant-false branch, unused member, unused local), message and fix hint.
- **Exemption**: A documented condition under which an apparently dead element is deliberately not reported.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the example suite, 100% of buggy dead-code examples are reported and 0 fixed examples are reported.
- **SC-002**: On a scan of codekoll's own sources, the dead-code rules report 0 findings that a maintainer judges to be false positives.
- **SC-003**: A scan with the new rules enabled adds no more than 10% to the time of the existing performance profile.
- **SC-004**: A developer can learn what a finding means and how to fix it from `--explain` alone, for 100% of the new rule IDs.

## Assumptions

- Cross-class detection of unused public or package-private members (whole-program analysis) is out of scope; the project constitution restricts v1 to method-local or single-class analysis, and it belongs under Future Work.
- Unused private members and unused local variables count as single-class analysis; this needs an explicit statement in the project's rule specification, which is updated with the change.
- Dead code is reported, never removed automatically.
- Findings follow the project's precision-over-recall principle: when in doubt, stay silent.
- Unused private members and locals (User Story 2) are in this feature. The project's rule specification is amended in the same change to list them as allowed single-class rules (Constitution Principle VI).
