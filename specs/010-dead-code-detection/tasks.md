# Tasks: Dead Code Detection

**Input**: `specs/010-dead-code-detection/plan.md` (Design is the detection contract), `spec.md`

**Tests**: test-first; every slice's test tasks are seen failing against its stubs before its
implementation task. Fixture tests use
`codekoll-engine/src/main/java/io/codekoll/engine/testing/RuleTestHarness.java: assertFixture(Rule, String, String)`
(marker `// :: <ID>` on each line that must have a finding, none elsewhere) and
`RuleTestHarness: run(Rule, String, String)` (returns `codekoll-engine/.../AnalysisResult.java: findings()` for message checks).
Style: `codekoll-rules/src/test/java/io/codekoll/rules/correctness/SelfAssignRuleTest.java`.

**Organization**: four rounds; a slice is its test tasks plus one implementation task.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: different files from every other [P] task of its step, no dependency on them
- **[Story]**: US1, US2, US3 of spec.md

---

## Round 1

### Slice 1: CK-DEAD-BRANCH (US1, P1)

**Goal**: constant-false `if` branches and `?:` arms are reported (FR-002).

- [ ] T001 [US1] Stub, shared by every slice: add `DEAD_CODE` after `PQC` in `codekoll-api/src/main/java/io/codekoll/api/RulePack.java: RulePack` (`id()` then yields `dead-code`); create `codekoll-rules/src/main/java/io/codekoll/rules/deadcode/package-info.java` after `codekoll-rules/src/main/java/io/codekoll/rules/correctness/package-info.java` (D5)
- [ ] T002 [P] [US1] Stub `codekoll-rules/src/main/java/io/codekoll/rules/deadcode/DeadBranchRule.java`: `public final class DeadBranchRule extends AbstractRule` with `RuleId id()` = `new RuleId("CK-DEAD-BRANCH")`, `RulePack pack()` = `RulePack.DEAD_CODE`, placeholder `Severity defaultSeverity()`, `String description()`, `String explanation()`, `String fix()`, and `protected TreePathScanner<Void, RuleContext> scanner()` returning a scanner that reports nothing; shape of `codekoll-rules/src/main/java/io/codekoll/rules/correctness/SelfAssignRule.java`
- [ ] T003 [P] [US1] Test `codekoll-rules/src/test/java/io/codekoll/rules/deadcode/DeadBranchRuleTest.java` covering FR-002, FR-005, FR-007, FR-008, FR-010, US1 scenarios 2, 3, 4. Not settled by spec: the evaluated operator set and the anchors in plan.md "Literal conditions" and "CK-DEAD-BRANCH"; severity WARNING, pack `DEAD_CODE`, non-blank `description()`/`explanation()`/`fix()`; message names the dead branch; negatives for a bare, a negated and a compound named-constant flag (D2), `do { } while (false)`, an operand that is a call or a string, a default-only `switch`, an empty method
- [ ] T004 [US1] Implement `DeadBranchRule` and new `codekoll-rules/src/main/java/io/codekoll/rules/deadcode/LiteralConditions.java`: `final class LiteralConditions { enum Truth { TRUE, FALSE, UNKNOWN } static Truth valueOf(ExpressionTree condition) }` per plan.md "Literal conditions"; strip parentheses with `codekoll-rules/src/main/java/io/codekoll/rules/support/NullFacts.java: unwrap(ExpressionTree)`; report with `codekoll-rules/src/main/java/io/codekoll/rules/support/RuleContext.java: report(Tree, String)`. Not registered until T017

### Slice 2: CK-UNUSED-LOCAL (US2, P2)

**Goal**: locals never read are reported (FR-003, locals).

- [ ] T005 [P] [US2] Stub `codekoll-rules/src/main/java/io/codekoll/rules/deadcode/UnusedLocalRule.java`: `public final class UnusedLocalRule extends AbstractRule`, `id()` = `new RuleId("CK-UNUSED-LOCAL")`, `pack()` = `RulePack.DEAD_CODE`, other members as T002
- [ ] T006 [P] [US2] Test `codekoll-rules/src/test/java/io/codekoll/rules/deadcode/UnusedLocalRuleTest.java` covering FR-003, FR-005, FR-007, FR-008, FR-010, US2 independent test. Not settled by spec: plan.md "Reads" and "CK-UNUSED-LOCAL": a write-only local fires with "never read" wording, a compound assignment or `++` counts as a read, a read inside a lambda or local class counts, `arr[i] = v` keeps `arr` and `i` used; severity INFO, pack, non-blank metadata, message names the local; one negative per exemption (enhanced-for variable, `_`, annotated local, `ignored`/`unused` names) and one each for parameter, catch parameter, try-with-resources variable, pattern binding
- [ ] T007 [P] [US2] Implement `UnusedLocalRule` and new `codekoll-rules/src/main/java/io/codekoll/rules/deadcode/Reads.java`: `final class Reads { static Set<Element> in(CompilationUnitTree unit, Trees trees) }` per plan.md "Reads"; call it from `visitCompilationUnit` with `RuleContext: unit()`, `trees()`; element kind via `Element.getKind() == ElementKind.LOCAL_VARIABLE`; report as T004

### Slice 3: SPEC entry (US2, FR-011)

- [ ] T008 [P] [US2] Docs `SPEC.md`: line 19 (non-goal) and line 318 (section 6 intro) name the single-class rules, CK-PROXY-SELF-INVOKE, CK-UNUSED-PRIVATE and CK-UNUSED-LOCAL; add `### 6.12 Pack dead-code` after section 6.11 with the four rows (ID, Sev, Detection, exemptions in italics) from plan.md Design; add a section 11 bullet for cross-class detection of unused non-private members. Section 4 counts stay for T017, which `RegistryConsistencyTest` ties to registration. FR-011 (No test: docs)

---

## Round 2

### Slice 4: CK-UNREACHABLE (US1, P1)

**Goal**: the statement after a literal-true `if` that always exits is reported (FR-001, D1).

- [ ] T009 [P] [US1] Stub `codekoll-rules/src/main/java/io/codekoll/rules/deadcode/UnreachableRule.java`: `public final class UnreachableRule extends AbstractRule`, `id()` = `new RuleId("CK-UNREACHABLE")`, `pack()` = `RulePack.DEAD_CODE`, other members as T002
- [ ] T010 [P] [US1] Test `codekoll-rules/src/test/java/io/codekoll/rules/deadcode/UnreachableRuleTest.java` covering FR-001, FR-005, FR-007, FR-010, US1 scenarios 1 and 3. Not settled by spec: plan.md "CK-UNREACHABLE": each exit kind (`return`, `throw`, `break`, `continue`, `yield`), braced and unbraced, exit as last statement of a nested block, inside a switch case; finding on the next statement only; severity WARNING, pack, non-blank metadata, message names the statement; negatives for a then-branch that completes normally, an `if` with an else, a named-constant condition (D2), the `if` as last statement, an empty method
- [ ] T011 [P] [US1] Implement `UnreachableRule` with `codekoll-rules/src/main/java/io/codekoll/rules/deadcode/LiteralConditions.java: valueOf` (T004); visit `BlockTree.getStatements()` and `CaseTree.getStatements()`; report as T004. Move `codekoll-rules/src/main/java/io/codekoll/rules/correctness/SwitchFallthroughRule.java: endsWithJump` unchanged into new `codekoll-rules/src/main/java/io/codekoll/rules/support/Jumps.java`: `public final class Jumps { public static boolean endsWithJump(List<? extends StatementTree> statements) }` (non-empty list, as today); `SwitchFallthroughRule` and `UnreachableRule` (`List.of(thenStatement)` when unbraced) call it, per plan.md "CK-UNREACHABLE"; `codekoll-rules/src/test/java/io/codekoll/rules/correctness/CorrectnessBatch9RulesTest.java` keeps `SwitchFallthroughRule` covered

### Slice 5: CK-UNUSED-PRIVATE (US2, P2)

**Goal**: private methods and fields never used in their class are reported (FR-003, FR-004, FR-009).

- [ ] T012 [P] [US2] Stub `codekoll-rules/src/main/java/io/codekoll/rules/deadcode/UnusedPrivateRule.java`: `public final class UnusedPrivateRule extends AbstractRule`, `id()` = `new RuleId("CK-UNUSED-PRIVATE")`, `pack()` = `RulePack.DEAD_CODE`, other members as T002
- [ ] T013 [P] [US2] Test `codekoll-rules/src/test/java/io/codekoll/rules/deadcode/UnusedPrivateRuleTest.java` covering FR-003, FR-004, FR-005, FR-007, FR-008, FR-009, FR-010, US2 scenarios 1 to 4. Not settled by spec: plan.md "CK-UNUSED-PRIVATE": a never-referenced field, a written-but-never-read field ("never read" wording), an unused private member of a nested class, a member used only from a nested class (silent), `holder.f = v` and `this.cfg.x = v` keep the private fields `holder` and `cfg` used (silent); one negative per exemption 1 to 7 (method reference and lambda each, member annotation, type annotation on a field's class, each serialization member, string-literal name, annotated same-name method, record component); private constructor and non-private members silent; severity INFO, pack, non-blank metadata, message names the member
- [ ] T014 [P] [US2] Implement `UnusedPrivateRule` with `codekoll-rules/src/main/java/io/codekoll/rules/deadcode/Reads.java: in` (T007); annotations via `Element.getAnnotationMirrors()`, record components via `TypeElement.getRecordComponents()`, private via `Element.getModifiers()`; report as T004

---

## Round 3

### Slice 6: registration, examples, CLI (US3, P3)

**Goal**: the four rules ship: every format, `--explain`, pack disable, severity, suppression (FR-005, FR-006, FR-008, SC-001, SC-004).

- [ ] T015 [P] [US3] Examples, checked by `codekoll-examples/src/test/java/examples/ExampleVerificationTest.java`: `codekoll-examples/src/main/java/examples/deadcode/UnreachableExample.java`, `DeadBranchExample.java`, `UnusedPrivateExample.java`, `UnusedLocalExample.java`, each with the Javadoc sections What is wrong / What happens at runtime / How to fix it, a `buggy()` part with `// :: <ID>` markers and a silent `fixed()` part, after `codekoll-examples/src/main/java/examples/correctness/SelfAssignExample.java`. Covers FR-008, SC-001. `DeadBranchExample.fixed()` uses the constant-flag idiom (US1 scenario 4); `buggy()`/`fixed()` stay non-private and free of unintended dead code
- [ ] T016 [P] [US3] Test `codekoll-cli/src/test/java/io/codekoll/cli/DeadCodeCliTest.java` covering FR-005, FR-006, SC-004, US3 scenarios 1 to 3: a temp repository with one finding per new rule, built like `codekoll-cli/src/test/java/io/codekoll/cli/ConfigCliTest.java` (`@TempDir repo`, `writeRepository`, `config(String)`, `run(String...)`, private there, so copied); findings with rule ID, file and line in `--format console`, `json` and `sarif`; `[rules] disable-packs = ["dead-code"]` silences all four; a `[severity]` override to `error` changes the exit code; `codekoll:off <ID>` drops one finding; `--explain <ID>` for each ID prints that rule's `explanation()` and `fix()`, captured as in `codekoll-cli/src/test/java/io/codekoll/cli/MainTest.java: run`
- [ ] T017 [US3] Register `DeadBranchRule`, `UnreachableRule`, `UnusedLocalRule`, `UnusedPrivateRule` in `codekoll-rules/src/main/java/module-info.java` `provides` and `codekoll-rules/src/main/resources/META-INF/services/io.codekoll.api.Rule`; resolve findings they raise in other examples per plan.md "Wiring and existing behaviour"; update the counts `codekoll-cli/src/test/java/io/codekoll/cli/RegistryConsistencyTest.java: documentedRuleAndPackCountsMatchTheRegistry` checks: `README.md` lines 5 and 9 (badges 117), 35, 39, 87 (117), 49 ("117 rules in twelve packs", reworded around the `pqc` sentence) and a `dead-code` row in its pack table; `SPEC.md` section 4 ("twelve packs", `dead-code` row 4, total 121, status paragraph 117 of 121); `PLAN.md` line 3 ("**117 specified rules**" becomes 121) and lines 21 and 24; `.claude/skills/codekoll/SKILL.md` line 11; regenerate `docs/RULES.md` with `java -jar codekoll-cli/target/codekoll.jar --catalog`. ARCHITECTURE.md unchanged (plan.md Constitution Check)

---

## Round 4

### Slice 7: manual checks (SC-002, SC-003)

- [ ] T018 [US3] Run both checks of `specs/010-dead-code-detection/quickstart.md` on the jar T017 builds and record, under a `## Results` heading at its end, the date, the finding count with each one judged true or false positive (SC-002), and the two medians and their ratio (SC-003). A false positive is fixed in its rule with a negative fixture in that rule's `codekoll-rules/src/test/java/io/codekoll/rules/deadcode/*RuleTest.java`, seen failing first (quickstart.md, Constitution Principle III); a ratio over 1.10 is reported, not tuned here. SC-002, SC-003 (No test: manual check, quickstart.md)

---

## Dependencies & Execution Order

- Round 1: T001 first (every stub compiles against `RulePack.DEAD_CODE`), then stubs T002, T005; tests T003, T006; implementations T004, T007, T008.
- Round 2 needs T004 (`LiteralConditions`) for T011 and T007 (`Reads`) for T014.
- Round 3 needs all four rules; T017 turns `ExampleVerificationTest` and `RegistryConsistencyTest` green together, so registration, examples and counts land in one slice.
- Round 4 needs T017 (the registered rules in the jar) for T018.

## Implementation Strategy

The smallest shippable cut is CK-DEAD-BRANCH (Slice 1) registered with its example. The planned
order builds all four rules unregistered first, so the full suite stays green until T017 registers
them together.
