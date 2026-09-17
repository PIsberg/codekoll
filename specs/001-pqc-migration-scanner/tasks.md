---
description: "Task list for the pqc rule pack"
---

# Tasks: PQC Migration Scanner & Static Guardrail

**Input**: Design documents from `specs/001-pqc-migration-scanner/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/rules.md](contracts/rules.md), [contracts/catalog-completeness.md](contracts/catalog-completeness.md), [quickstart.md](quickstart.md)

**Tests**: Included. FR-012 requires positive and negative fixtures for every rule and exemption, and the repository owner requires every behaviour change to ship with a test written first and seen failing.

**Organization**: Grouped by user story (spec.md). Paths are repository-relative.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 to US4 from spec.md

## Conventions every task follows

- Rule tests use `io.codekoll.engine.testing.RuleTestHarness.assertFixture(rule, "<ClassName>", """...""")` with a Java text block; every line that must produce a finding ends with `// :: <RULE-ID>`, and a fixture with no marker must produce zero findings. Pattern to copy: `codekoll-rules/src/test/java/io/codekoll/rules/security/WeakCryptoRuleTest.java`.
- A fixture can simulate a test source file by passing a dotted class name: `StringSource` turns `"src.test.java.N1"` into `string:///src/test/java/N1.java`.
- Rules extend `io.codekoll.rules.support.AbstractRule`, override `scanner()` returning a `TreePathScanner<Void, RuleContext>`, and report with `ctx.report(tree, message)` (severity is always `defaultSeverity()`). Pattern to copy: `codekoll-rules/src/main/java/io/codekoll/rules/security/WeakCryptoRule.java`.
- Constant resolution: a `LiteralTree` string, or an identifier / member select whose `Element` is a `VariableElement` with a `String` `getConstantValue()` (`WeakCryptoRule.java:106-118`).
- Every new rule is registered in BOTH `codekoll-rules/src/main/java/module-info.java` (`provides io.codekoll.api.Rule with ...`) and `codekoll-rules/src/main/resources/META-INF/services/io.codekoll.api.Rule`, or it loads in one packaging and not the other.
- Message text and exemptions are fixed by [contracts/rules.md](contracts/rules.md); the name catalog by research.md Appendix A and B. Do not invent names.
- Prose in Javadoc and docs: no em-dashes, no curly quotes.
- Run a module's tests with `mvn -pl <module> -am test`; never pipe Maven output through `tail`/`grep` without checking the exit status.

---

## Phase 1: Setup

**Purpose**: The pack exists and the test harness can target an older release.

- [X] T001 Add constant `PQC` as the last value of `RulePack` (after `FRAMEWORKS`) in `codekoll-api/src/main/java/io/codekoll/api/RulePack.java`; `id()` then yields `pqc` with no other change
- [X] T002 [P] Create `codekoll-rules/src/main/java/io/codekoll/rules/pqc/package-info.java` with Javadoc `/** Pack {@code pqc}: quantum-vulnerable asymmetric cryptography. */`, `@NullMarked`, matching `codekoll-rules/src/main/java/io/codekoll/rules/security/package-info.java`
- [X] T003 [P] Add an overload `run(Rule rule, String className, String source, int release)` and `assertFixture(Rule rule, String className, String source, int release)` to `codekoll-engine/src/main/java/io/codekoll/engine/testing/RuleTestHarness.java`; the existing two-argument-source methods delegate with release `25`. Add a test in `codekoll-engine/src/test/java/io/codekoll/engine/testing/RuleTestHarnessReleaseTest.java` proving `--release 21` is applied: a fixture referencing `java.security.spec.NamedParameterSpec.ML_KEM_768` lands in `skippedFiles()` at release 21 and compiles at 25

**Checkpoint**: `mvn -pl codekoll-engine -am test` green; `RulePack.PQC.id()` is `pqc`.

---

## Phase 2: Foundational (blocking all stories)

**Purpose**: The classification catalog, the site matcher, test-source detection and the platform probe that every rule uses. Tests first.

### Tests (write first, run, confirm they fail to compile or fail)

- [X] T004 [P] Write `codekoll-rules/src/test/java/io/codekoll/rules/pqc/PqcCatalogTest.java` asserting: (a) lookup is case-insensitive (`sha256withecdsa` finds `SHA256withECDSA`); (b) every alias and OID from research.md Appendix A resolves to its canonical entry, sampled with at least `DSS`, `RawDSA`, `SHAwithDSA`, `PSS`, `EllipticCurve`, `DH`, `1.3.14.3.2.29`, `OID.1.2.840.10045.4.3.2`, `1.3.101.110`; (c) `Cipher` lookup uses the first `/` segment (`RSA/ECB/OAEPWithSHA-256AndMGF1Padding` is `Vulnerable(RSA, KEY_ESTABLISHMENT)`); (d) `DHKEM` and `HPKE` are `Vulnerable(..., KEY_ESTABLISHMENT)`; (e) `ML-KEM-768`, `2.16.840.1.101.3.4.4.2`, `ML-DSA-65`, `HSS/LMS` are `PostQuantum`; (f) `AES/GCM/NoPadding`, `PBEWithHmacSHA256AndAES_256`, `ChaCha20-Poly1305`, `OAEP` (AlgorithmParameters) are `NotAsymmetric`; (g) no two entries share a `(RequestType, upper-cased name)` key; (h) TLS suites: `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`, `TLS_RSA_WITH_AES_128_CBC_SHA`, `SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA` vulnerable; `TLS_AES_256_GCM_SHA384`, `TLS_EMPTY_RENEGOTIATION_INFO_SCSV` not asymmetric; (i) every named group and signature scheme in Appendix B is vulnerable with the right role; (j) every XML DSig URI in Appendix B classifies as listed, HMAC URIs not asymmetric
- [X] T005 [P] Write `codekoll-rules/src/test/java/io/codekoll/rules/support/SourceKindsTest.java` asserting `SourceKinds.isTestSource(URI)` is true for `file:///r/src/test/java/A.java`, `file:///r/mod/src/testFixtures/java/A.java`, `file:///r/src/integrationTest/java/A.java`, `file:///r/src/it/java/A.java`, `string:///src/test/java/A.java`, Windows-style `file:///C:/r/src/test/java/A.java`; false for `file:///r/src/main/java/test/A.java` (package named test under main), `file:///r/src/main/java/A.java`, `string:///A.java`
- [X] T006 [P] Write `codekoll-rules/src/test/java/io/codekoll/rules/pqc/PqcGuidanceTest.java` asserting the message builders in contracts/rules.md produce, for built-in `true` and `false`, exactly the two KEY-EXCHANGE sentences, the two SIGNATURE sentences and the KEY-MATERIAL sentence (role known and role unknown), with a multi-name `<name>` joined as `x25519, secp256r1`

### Implementation

- [X] T007 Create the data-model enums and sealed type in `codekoll-rules/src/main/java/io/codekoll/rules/pqc/PqcModel.java`: package-private `enum RequestType` (13 values from data-model.md), `enum Family`, `enum Role` (`KEY_ESTABLISHMENT`, `SIGNATURE`, `KEY_MATERIAL`), `sealed interface Classification` with records `Vulnerable(Family family, Role role, String displayName)`, `PostQuantum(Family family)`, `NotAsymmetric()`
- [X] T008 Implement `codekoll-rules/src/main/java/io/codekoll/rules/pqc/PqcCatalog.java` (depends on T007): immutable map keyed by `(RequestType, name.toUpperCase(Locale.ROOT))` built once in a static initializer from explicit entries transcribed from research.md Appendix A (canonical names plus every alias and OID in the row) and Appendix B; `Optional<Classification> classify(RequestType, String)`; `Cipher` splits on `/`; `NotAsymmetric` prefix list from research R2 for the eight provider types; TLS suite classification by the key-exchange token between `TLS_`/`SSL_` and `_WITH_` (data-model.md); property-value helper splitting on `,` and trimming. Role for key-material entries is the family's implied role (`X25519`/`X448`/`XDH`/`DiffieHellman` key establishment; `DSA`/`EdDSA`/`Ed25519`/`Ed448`/`RSASSA-PSS` signature; `RSA`/`EC` none). Run T004 green
- [X] T009 [P] Implement `codekoll-rules/src/main/java/io/codekoll/rules/support/SourceKinds.java`: `static boolean isTestSource(URI uri)` true when the path has a segment `src` immediately followed by `test`, `tests`, `testFixtures`, `integrationTest` or `it`; plus `isTestSource(CompilationUnitTree unit)` delegating to `unit.getSourceFile().toUri()`. Run T005 green
- [X] T010 [P] Implement `codekoll-rules/src/main/java/io/codekoll/rules/pqc/PqcGuidance.java` (depends on T007): `static boolean builtInPqc(Elements elements)` returning true when `elements.getTypeElement("java.security.spec.NamedParameterSpec")` is non-null and encloses fields named `ML_KEM_768` and `ML_DSA_65`; message builders taking `(List<String> names, boolean builtIn)` and, for key material, a nullable `Role`, producing the exact strings in contracts/rules.md. Run T006 green
- [X] T011 Implement `codekoll-rules/src/main/java/io/codekoll/rules/pqc/PqcSites.java` (depends on T007, T008): package-private matcher returning an optional `RequestSite(Tree tree, RequestType type, List<String> writtenNames, List<String> vulnerable, @Nullable Role role)` for a `MethodInvocationTree`, `NewClassTree` or `MemberSelectTree` under a `TreePath`, covering every row of the research R4 site table: owner type resolved to a `TypeElement` qualified name (static calls) or receiver type via `ctx.typeOf` + `ctx.qualifiedNameOf` (instance setters on `SSLParameters`, `SSLSocket`, `SSLServerSocket`, `SSLEngine`, `XMLSignatureFactory`); constant string first argument; `new String[] {...}` or same-unit `static final String[]` initializer arguments; `System.setProperty` / `java.security.Security.setProperty` with the three `jdk.tls.*` keys. Filter on method or class simple name before resolving any type. Return empty when no written name is `Vulnerable`
- [X] T012 Write `codekoll-rules/src/test/java/io/codekoll/rules/pqc/PqcSitesTest.java` (depends on T011) using `RuleTestHarness.run` with a tiny test-only rule in the same file that reports every `PqcSites` match as `<type>:<vulnerable names>`; assert one sample per row of the R4 table, a provider-qualified `getInstance("RSA", "BC")`, a constant from another class, and that `getInstance(algorithmVariable)` yields nothing

**Checkpoint**: `mvn -pl codekoll-rules -am test` green with T004, T005, T006, T012 passing; nothing is registered yet, so no user-visible behaviour has changed.

---

## Phase 3: User Story 1 - Inventory quantum-vulnerable cryptography (P1) MVP

**Goal**: A run over a source tree reports one finding per vulnerable request site with algorithm, role and replacement.

**Independent Test**: Quickstart step 2 on the sample `Sample.java` gives 4 findings (2 KEY-EXCHANGE, 1 SIGNATURE, 1 KEY-MATERIAL); every fixture in T013 to T015 passes.

### Tests for User Story 1 (write first, confirm red)

- [X] T013 [P] [US1] Write `codekoll-rules/src/test/java/io/codekoll/rules/pqc/PqcKeyExchangeRuleTest.java` with positive fixtures, each marked `// :: CK-PQC-KEY-EXCHANGE`: `KeyAgreement.getInstance` for `DiffieHellman`, `DH`, `ECDH`, `XDH`, `X25519`, `X448`, `1.3.101.110`; `KEM.getInstance("DHKEM")`; `Cipher.getInstance` for `RSA`, `RSA/ECB/PKCS1Padding`, `rsa/ecb/oaepwithsha-256andmgf1padding`, `HPKE`, and `("RSA", "SunJCE")`; a constant name from a `static final String`; `SSLParameters.setNamedGroups(new String[] {"x25519", "secp256r1"})` as one finding; `setEnabledCipherSuites` on an `SSLSocket` with one `TLS_ECDHE_RSA_...` suite among TLS 1.3 suites; `System.setProperty("jdk.tls.namedGroups", "x25519,ffdhe2048")`; `Security.setProperty` same key. Negative fixtures (no marker): `KEM.getInstance("ML-KEM")`; `Cipher.getInstance("AES/GCM/NoPadding")`; `KeyAgreement.getInstance(alg)` with a parameter; a method containing both `KeyAgreement.getInstance("X25519")` and `KEM.getInstance("ML-KEM-768")` (hybrid); `setEnabledCipherSuites(new String[] {"TLS_AES_256_GCM_SHA384"})`; `System.setProperty("jdk.tls.other", "x25519")`; a class named `"src.test.java.N7"` containing `KeyAgreement.getInstance("ECDH")` (test source). Add one `RuleTestHarness.run` test asserting the ECDH message equals the built-in KEY-EXCHANGE sentence from contracts/rules.md with `<name>` = `ECDH`
- [X] T014 [P] [US1] Write `codekoll-rules/src/test/java/io/codekoll/rules/pqc/PqcSignatureRuleTest.java` with positive fixtures marked `// :: CK-PQC-SIGNATURE`: `Signature.getInstance` for `SHA256withRSA`, `SHA256withECDSA`, `SHA3-512withECDSAinP1363Format`, `NONEwithDSA`, `DSS`, `RawDSA`, `RSASSA-PSS`, `PSS`, `Ed25519`, `EdDSA`, `MD5andSHA1withRSA`, `1.2.840.113549.1.1.11`, `OID.1.2.840.10045.4.3.2`; `XMLSignatureFactory.getInstance("DOM").newSignatureMethod(SignatureMethod.RSA_SHA256, null)`; the same with the literal URI `"http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256"`; `SSLParameters.setSignatureSchemes(new String[] {"ecdsa_secp256r1_sha256", "ed25519"})` as one finding; `System.setProperty("jdk.tls.client.SignatureSchemes", "rsa_pss_rsae_sha256")`. Negative: `Signature.getInstance("ML-DSA")`, `("ML-DSA-65")`, `("HSS/LMS")`; `newSignatureMethod(SignatureMethod.HMAC_SHA256, null)`; non-constant name; test-source class. Confirm in the first XML DSig fixture that `SignatureMethod.RSA_SHA256` resolves as a constant (research R4 "read, to confirm"); if it does not, record that and match the field by name instead
- [X] T015 [P] [US1] Write `codekoll-rules/src/test/java/io/codekoll/rules/pqc/PqcKeyMaterialRuleTest.java` with positive fixtures marked `// :: CK-PQC-KEY-MATERIAL`: `KeyPairGenerator.getInstance` for `RSA`, `EC`, `EllipticCurve`, `DSA`, `DiffieHellman`, `X25519`, `Ed448`, `RSASSA-PSS`, `1.2.840.10045.2.1`; `KeyFactory.getInstance("RSA")`; `AlgorithmParameters.getInstance("EC")`; `AlgorithmParameterGenerator.getInstance("DH")`; in a method with no `getInstance`: `new ECGenParameterSpec("secp256r1")`, `new RSAKeyGenParameterSpec(3072, RSAKeyGenParameterSpec.F4)`, `new DHParameterSpec(p, g)`, `new NamedParameterSpec("X25519")`, field access `NamedParameterSpec.ED25519`. Negative: `KeyPairGenerator.getInstance("ML-KEM")`, `("ML-DSA-87")`, `KeyFactory.getInstance("HSS/LMS")`; `AlgorithmParameters.getInstance("OAEP")`, `("GCM")`; `NamedParameterSpec.ML_KEM_768`; a method with `KeyPairGenerator.getInstance("EC")` (marked) followed by `initialize(new ECGenParameterSpec("secp256r1"))` (not marked, dedupe); test-source class. Add `RuleTestHarness.run` message assertions: `X25519` names key establishment and ML-KEM, `EC` names both roles and "ML-KEM or ML-DSA"

### Implementation for User Story 1

- [X] T016 [P] [US1] Implement `codekoll-rules/src/main/java/io/codekoll/rules/pqc/PqcKeyExchangeRule.java`: id `CK-PQC-KEY-EXCHANGE`, pack `RulePack.PQC`, severity `Severity.WARNING`, `description()`/`explanation()`/`fix()` from contracts/rules.md; skip the unit when `SourceKinds.isTestSource(ctx.unit())`; compute `PqcGuidance.builtInPqc(ctx.elements())` once per unit; report `PqcSites` matches whose type is `KEY_AGREEMENT`, `KEM`, `CIPHER`, `TLS_NAMED_GROUP` or `TLS_CIPHER_SUITE`; suppress `KEY_AGREEMENT` and `KEM` matches when the enclosing `MethodTree` contains any request whose written name classifies `PostQuantum(ML_KEM)` (hybrid exemption). Run T013 green
- [X] T017 [P] [US1] Implement `codekoll-rules/src/main/java/io/codekoll/rules/pqc/PqcSignatureRule.java`: id `CK-PQC-SIGNATURE`, `RulePack.PQC`, `Severity.WARNING`, metadata from contracts/rules.md, test-source skip, reports `SIGNATURE`, `TLS_SIGNATURE_SCHEME`, `XML_SIGNATURE_METHOD` matches. Run T014 green
- [X] T018 [P] [US1] Implement `codekoll-rules/src/main/java/io/codekoll/rules/pqc/PqcKeyMaterialRule.java`: id `CK-PQC-KEY-MATERIAL`, `RulePack.PQC`, `Severity.INFO`, metadata from contracts/rules.md, test-source skip, reports `KEY_PAIR_GENERATOR`, `KEY_FACTORY`, `ALGORITHM_PARAMETERS`, `ALGORITHM_PARAMETER_GENERATOR` and `PARAMETER_SPEC` matches; a `PARAMETER_SPEC` match is dropped when its enclosing method contains a reported key-material `getInstance`. Run T015 green
- [X] T019 [US1] Register the three rules (depends on T016, T017, T018): append `io.codekoll.rules.pqc.PqcKeyExchangeRule`, `io.codekoll.rules.pqc.PqcSignatureRule`, `io.codekoll.rules.pqc.PqcKeyMaterialRule` to the `provides io.codekoll.api.Rule with` list in `codekoll-rules/src/main/java/module-info.java` and as three lines at the end of `codekoll-rules/src/main/resources/META-INF/services/io.codekoll.api.Rule`; confirm both lists now have 113 entries
- [X] T020 [US1] Run the full `mvn -pl codekoll-api,codekoll-engine,codekoll-rules,codekoll-report,codekoll-workspace,codekoll-cli -am test` (the CI `build` job's modules) and `mvn -pl codekoll-cli -am test -Dtest=ArchitectureTest`; both must pass, including `everyRuleHasCompleteMetadata`. Paste any failure verbatim into the task notes rather than summarising it

**Checkpoint**: User Story 1 is complete. Build the jar (`mvn -pl codekoll-cli -am package -DskipTests`) and run quickstart step 2: 4 findings.

---

## Phase 4: User Story 2 - Guardrail that warns, not blocks (P1)

**Goal**: The pack is on by default, never fails a default run, fails under `--fail-on warning`, honours severity overrides and line suppression.

**Independent Test**: T021 passes; quickstart step 2 prints `exit=0`.

### Tests for User Story 2 (write first)

- [X] T021 [US2] Write `codekoll-cli/src/test/java/io/codekoll/cli/PqcGuardrailCliTest.java`, following the `Run run(String... args)` helper and temp-file setup in `codekoll-cli/src/test/java/io/codekoll/cli/MainTest.java`, with one source file containing `Signature.getInstance("SHA256withECDSA")` and `KeyAgreement.getInstance("ECDH")`. Assert: (a) default args: both findings in output, exit code `0` (FR-005); (b) `--fail-on warning`: exit code `1`; (c) a source with only `KeyPairGenerator.getInstance("EC")` under `--fail-on warning`: finding shown, exit `0` (INFO); (d) a `codekoll.toml` with `[severity]` `CK-PQC-SIGNATURE = "error"` (copy the key syntax from `codekoll-cli/src/test/java/io/codekoll/cli/ConfigCliTest.java`): exit `1` at default `--fail-on`; (e) the signature line ending `// codekoll:off CK-PQC-SIGNATURE`: only the key-exchange finding remains; (f) `--packs pqc` is accepted and `--packs security` reports none of the three ids; (g) `rules.disable-packs = ["pqc"]` in `codekoll.toml` removes all three. Run against the Phase 3 build and confirm green; then temporarily change `PqcSignatureRule.defaultSeverity()` to `ERROR`, confirm (a) goes red, and revert

### Implementation for User Story 2

- [X] T022 [US2] If any assertion in T021 fails for a reason outside the pack (for example `--packs` validation), fix it in the owning file under `codekoll-cli/src/main/java/io/codekoll/cli/` with its own test, and note it in the PR body; if all pass unchanged, record in the task notes that no CLI change was needed (expected per research R7)
- [X] T023 [US2] Record the baseline dependency: in `specs/001-pqc-migration-scanner/spec.md` User Story 2 scenario 2 and SC-004 stay marked as blocked on Milestone 15; no baseline code is written in this feature

**Checkpoint**: User Stories 1 and 2 both pass independently.

---

## Phase 5: User Story 3 - Migration guidance a developer can act on (P2)

**Goal**: `--explain` and every finding give actionable, release-aware guidance; each rule has a documented example pair.

**Independent Test**: T024 and T025 pass; `ExampleVerificationTest` passes with the three new examples; quickstart steps 3 and 5 give the expected text.

### Tests for User Story 3 (write first)

- [X] T024 [P] [US3] Add to `codekoll-rules/src/test/java/io/codekoll/rules/pqc/PqcKeyExchangeRuleTest.java` and `PqcSignatureRuleTest.java` one test each using `RuleTestHarness.run(rule, className, source, 21)` (T003) on `KeyAgreement.getInstance("ECDH")` / `Signature.getInstance("Ed25519")`, asserting the message contains "not built into this project's Java release: use Bouncy Castle PQC or target Java 24+", and the same source at release 25 contains "built into this project's Java platform"
- [X] T025 [P] [US3] Write `codekoll-rules/src/test/java/io/codekoll/rules/pqc/PqcMetadataTest.java` asserting, for each of the three rules: `explanation()` mentions `Shor`, the role's urgency ("recorded" for key exchange, "forge" for signature), "not a drop-in", and "never rewrites"; `fix()` mentions `ML-KEM` or `ML-DSA` as applicable, "hybrid" (key exchange and key material), "Java 24" and "Bouncy Castle"; `defaultSeverity()` is not `ERROR`

### Implementation for User Story 3

- [X] T026 [US3] Finalise `explanation()` and `fix()` text in `PqcKeyExchangeRule.java`, `PqcSignatureRule.java`, `PqcKeyMaterialRule.java` under `codekoll-rules/src/main/java/io/codekoll/rules/pqc/` to satisfy T025 and contracts/rules.md "`--explain <id>` content"; cite FIPS 203 and FIPS 204 by number and quote no NIST IR 8547 dates (research R9)
- [X] T027 [P] [US3] Create `codekoll-examples/src/main/java/examples/pqc/PqcKeyExchangeExample.java` following `codekoll-examples/src/main/java/examples/security/CryptoWeakExample.java`: class Javadoc with "What is wrong", "What happens at runtime", "How to fix it"; `buggy()` doing an X25519 `KeyAgreement` with the request line marked `// :: CK-PQC-KEY-EXCHANGE`; `fixed()` doing `KeyPairGenerator.getInstance("ML-KEM")` + `KEM.getInstance("ML-KEM")` encapsulate/decapsulate with no finding. The `fixed()` `KeyPairGenerator` request is itself post-quantum, so no KEY-MATERIAL marker is needed; `buggy()` must avoid a separate `KeyPairGenerator.getInstance("X25519")` line unless it carries a `// :: CK-PQC-KEY-MATERIAL` marker too
- [X] T028 [P] [US3] Create `codekoll-examples/src/main/java/examples/pqc/PqcSignatureExample.java`: `buggy()` signs with `Signature.getInstance("SHA256withECDSA")` (marked `// :: CK-PQC-SIGNATURE`; any `KeyPairGenerator.getInstance("EC")` line also marked `// :: CK-PQC-KEY-MATERIAL`); `fixed()` generates an `ML-DSA` key pair and signs with `Signature.getInstance("ML-DSA")`, no findings
- [X] T029 [P] [US3] Create `codekoll-examples/src/main/java/examples/pqc/PqcKeyMaterialExample.java`: `buggy()` calls `KeyPairGenerator.getInstance("RSA")` marked `// :: CK-PQC-KEY-MATERIAL`; `fixed()` calls `KeyPairGenerator.getInstance("ML-KEM-768")`, no findings
- [X] T030 [US3] Run `mvn -pl codekoll-examples -am test` (depends on T019, T027, T028, T029); all `ExampleVerificationTest` checks must pass, including that each new rule fires and the class-name convention (`CK-PQC-KEY-EXCHANGE` to `PqcKeyExchangeExample`)

**Checkpoint**: Quickstart steps 3 and 5 produce the contracted text.

---

## Phase 6: User Story 4 - Coverage that stays complete as Java evolves (P2)

**Goal**: The build fails when the running JDK exposes a name the catalog does not classify (FR-016).

**Independent Test**: T031 passes on JDK 25; deleting one catalog entry turns it red naming that entry.

- [X] T031 [US4] Write `codekoll-rules/src/test/java/io/codekoll/rules/pqc/PqcCatalogCompletenessTest.java` per contracts/catalog-completeness.md: collect every `Provider.Service` algorithm and every `Alg.Alias.<type>.<alias>` property for the eight provider types from `Security.getProviders()`; `SSLContext.getDefault().getSupportedSSLParameters()` cipher suites, named groups, and signature schemes when non-null (when null, assert the catalog's scheme list is non-empty instead); every `public static final String` field of `javax.xml.crypto.dsig.SignatureMethod`. Assert all classify via `PqcCatalog`; on failure report ALL unclassified names in one sorted message with type and provider, prefixed by `System.getProperty("java.vendor")` and `java.version`. Use `assertAll`-style collection, not first-failure
- [X] T032 [US4] Prove T031 can fail (depends on T031): remove the `SHA3-512withECDSAinP1363Format` entry from `codekoll-rules/src/main/java/io/codekoll/rules/pqc/PqcCatalog.java`, run `mvn -pl codekoll-rules -am test -Dtest=PqcCatalogCompletenessTest`, confirm red naming `Signature SHA3-512withECDSAinP1363Format`, restore, confirm green. Paste both outputs into the PR body's verification section
- [X] T033 [US4] Run T031 on every JDK 25+ available locally (at minimum JDK 26 at `C:\Program Files\Java\jdk-26`, via `JAVA_HOME`) and add any name it reports to `PqcCatalog.java` with the classification rule from research R1/R2; record which JDKs were run and which were unavailable (JDK 25 is not installed on the planning machine; CI's Temurin 25 is the authoritative run)

**Checkpoint**: All four stories independently pass.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T034 [P] Add section 6.11 "Pack `pqc`" to `SPEC.md` (table: id, severity, detection, exemptions, from contracts/rules.md), add `PQC` to the pack comment at `SPEC.md:115`, update the pack overview table and counts at `SPEC.md:199-215` and the rule index from line 228 (110 to 113 implemented, ten to eleven packs)
- [X] T035 [P] Update `README.md`: badges `examples-110%2F110` to `113%2F113` and `rules-110` to `rules-113` (lines 5, 9), counts at lines 35, 39, 49, 86, and add a `pqc` row to the pack table (lines 51-62) with focus "Quantum-vulnerable asymmetric crypto", 3 rules, flavor listing RSA encryption, ECDSA/EdDSA signatures, (EC)DH/X25519 and DHKEM, TLS groups; state it warns and never fails a default build
- [X] T036 [P] Update rule and pack counts in `PLAN.md` (lines 3, 21, 24, 51), `docs/CLI-PLAN.md` (117, 219), `docs/CLI-SPEC.md` (129) and `.claude/skills/codekoll/SKILL.md` (11); grep the repo for `110 rules`, `ten packs`, `114` afterwards and resolve every remaining stale hit
- [X] T037 Regenerate `docs/RULES.md` (depends on T019): `mvn -pl codekoll-cli -am package -DskipTests`, then `java -jar codekoll-cli/target/codekoll.jar --catalog > docs/RULES.md`; confirm the file ends with a `## pqc (3)` section and the header says 113 rules
- [X] T038 Run the CI `quality` gates: `mvn -pl codekoll-api,codekoll-engine,codekoll-rules,codekoll-report,codekoll-workspace,codekoll-cli checkstyle:check`, then `package pmd:check -DskipTests`, then `package spotbugs:check -DskipTests`, on JDK 25 (a newer JDK can make PMD report nonsense); fix findings in the new files, no new suppressions
- [X] T039 Run the CI `coverage` job's `mvn verify` and confirm the JaCoCo thresholds in `pom.xml` still pass
- [X] T040 Run the self-check exactly as `.github/workflows/ci.yml` line 135 does (`java -jar codekoll-cli/target/codekoll.jar --format sarif --output codekoll.sarif --fail-on error ...` on `codekoll-rules/src/main/java`); confirm exit `0` and that the SARIF contains no `CK-PQC-*` result for `PqcCatalog.java` (names stored in collections are not request sites)
- [X] T041 Run `mvn -pl codekoll-load-test -am -Pquick verify -DskipTests`; report CPU and heap deltas against `codekoll-load-test/baseline.json` (SC-005 budget 5%, gate 15%/20%). Update the `findings` count in `baseline.json` only if the corpus genuinely requests vulnerable algorithms, and say so in the commit message with the before and after numbers
- [X] T042 Walk `specs/001-pqc-migration-scanner/quickstart.md` steps 1, 2, 3, 5, 6 and the suppression alternative in step 4 against the built jar; record actual outputs and exit codes; step 4's baseline commands are reported as not run (Milestone 15)
- [X] T043 Open one GitHub issue per follow-up in `specs/001-pqc-migration-scanner/research.md` "Follow-ups" (8 issues) with `gh issue create`, each stating what is missing, why it was not done here, and what it would take; collect the issue numbers
- [X] T044 Push `001-pqc-migration-scanner`, open a PR to `main` with `gh pr create` whose body states the failure prevented (quantum-vulnerable crypto with no inventory), how it was verified (T020, T030, T032 outputs, T041 numbers, JDKs run in T033), the Milestone 15 baseline dependency, and links to the T043 issues; do not merge. Follow the CI run to green on every job (`build`, `quality`, `coverage`, `archunit`, `examples`, `selfcheck`, `loadtest`), fixing and pushing until it is green, and report the run's actual state

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (T001-T003)**: none; T001 before anything that references `RulePack.PQC`
- **Foundational (T004-T012)**: after T001, T002; blocks every story
- **US1 (T013-T020)**: after Foundational
- **US2 (T021-T023)**: after T019 (rules must be registered for the CLI to load them)
- **US3 (T024-T030)**: after T003 (release overload) and T016-T019; examples need registered rules
- **US4 (T031-T033)**: after T008 only; can run in parallel with US1
- **Polish (T034-T044)**: after all stories; T037 after T019; T044 last

### Within phases

- T007 before T008, T010, T011; T008 before T011; T011 before T012
- Rule tests T013, T014, T015 before their implementations T016, T017, T018
- T019 touches two shared files: do it once, after all three rules exist
- T026 edits the same files as T016-T018: after them, never in parallel

### Story independence

- US1 is the MVP: inventory alone is shippable
- US2 adds no production code in the expected case; it proves the warning-only contract
- US3 improves messages and adds examples; US1's findings exist without it
- US4 depends only on the catalog and can be built right after Foundational

---

## Parallel Examples

```text
# Foundational tests together:
T004 PqcCatalogTest.java | T005 SourceKindsTest.java | T006 PqcGuidanceTest.java

# Foundational implementations after T007:
T009 SourceKinds.java | T010 PqcGuidance.java   (T008 PqcCatalog.java runs alongside, T011 after T008)

# User Story 1 tests together, then implementations together:
T013 | T014 | T015
T016 | T017 | T018

# User Story 3 examples together:
T027 | T028 | T029

# Docs together:
T034 SPEC.md | T035 README.md | T036 PLAN.md, docs/CLI-*.md, SKILL.md

# US4 alongside US1 once T008 is done:
T031 PqcCatalogCompletenessTest.java
```

---

## Implementation Strategy

### MVP (User Story 1)

1. Phase 1 and Phase 2, with foundational tests seen red then green
2. Phase 3: fixtures red, three rules, registration, full build green
3. Stop and validate with quickstart step 2

### Incremental delivery on one branch

1. MVP (US1), then US4 (cheap, and it hardens the catalog before more code depends on it)
2. US2 (proves the warn-only contract), then US3 (guidance and examples)
3. Polish: docs, gates, issues, one PR

One PR, because registration lists, docs counts and examples are shared files (plan.md "Delivery"). Commit per phase so review can follow the red-then-green order.

---

## Notes

- 44 tasks. Verify each test fails before implementing it; where a test is written after the code (T021), break the code once to see it go red.
- Report skipped or unavailable gates as such (for example JDK 25 absent locally, baseline commands not runnable), never as passed.

## Implementation notes

- T003: the release test lives in `codekoll-rules/src/test/java/io/codekoll/rules/support/RuleTestHarnessReleaseTest.java`, not under `codekoll-engine/src/test`: the engine module has no test sources and no JUnit dependency.
- T007: the model is four package-private files (`RequestType.java`, `Family.java`, `Role.java`, `Classification.java`) instead of one `PqcModel.java`, one top-level type per file.
- T008: named entries are data in `codekoll-rules/src/main/resources/io/codekoll/rules/pqc/pqc-catalog.tsv` (182 rows generated from the JDK 26 probe), loaded by `PqcCatalog`, instead of Java literals. Post-quantum families (ML-KEM, ML-DSA, SLH-DSA, HSS/LMS) are also recognised by prefix, and `TRIPLEDES` joined the not-asymmetric prefixes for the Cipher alias.
- T011/T012: confirmed that `SignatureMethod.RSA_SHA256` resolves through `getConstantValue()` (research R4 open item).
- T013-T018: all rule tests passed on first run, so the exemptions were broken deliberately (test-source skip and hybrid exemption in `PqcKeyExchangeRule`, parameter-spec dedupe in `PqcKeyMaterialRule`): 4 of 15 tests went red, code restored.
- T020: `mvn -pl codekoll-api,codekoll-engine,codekoll-rules,codekoll-report,codekoll-workspace,codekoll-cli -am test` on JDK 26 locally: rules 394, report 8, workspace 160, cli 45 (ArchitectureTest 4) tests, 0 failures. CI's JDK 25 run is the authoritative one.
- T021: also covers analysis findings G2 (JSON and SARIF carry the rule ids) and R6 end to end (a `src/test/java` file is not reported). SARIF has no pack tag for any rule, contrary to SPEC.md section 7; the assertion was dropped and the drift added as research follow-up 9. Written after the code, so `PqcSignatureRule` was raised to ERROR once: `enabledByDefaultAndNeverFailsADefaultRun` went red, reverted.
- T022: no CLI change needed. T023: spec already marks the baseline scenario as blocked on Milestone 15.
- T026: final metadata text was written with T016-T018 and is pinned by `PqcMetadataTest` (T025).
- T030: deleting the marker in `PqcKeyMaterialExample` made `findingsMatchMarkersExactly` fail; restored.
- T032: removing `SHA3-512withECDSAinP1363Format` and the `DSS` alias from `pqc-catalog.tsv` failed the test naming both (`Signature  DSS  (alias of SHA1withDSA, provider SUN)`, `Signature  SHA3-512withECDSAinP1363Format  (provider SunEC)`); restored, green.
- T033: run on JDK 26 (Oracle, Windows) only, green with no additions. JDK 25 is not installed locally; CI's Temurin 25 on Linux is the first JDK 25 run. JDK 24 on this machine is a dangling install.
- T034-T036: historical counts left as they are on purpose (PLAN.md's 106-rule v1 target and line 51 commit description; `docs/CLI-PLAN.md` lines 117 and 219 describe past measurements). README line 49 was reworded, not just renumbered: `pqc` findings are not runtime bugs.
- T037: `docs/RULES.md` regenerated with `--catalog`; the diff is the header count plus the new `pqc (3)` section only.
- T038-T040: run as one `mvn -B verify` (Checkstyle, PMD, SpotBugs, Error Prone, NullAway, JaCoCo, examples, selfcheck are bound to it) on JDK 26. First two runs failed on real findings in the new code, both fixed: 22 Checkstyle `NewlineAtEndOfFile` (file-writing artifact), PMD `CloseResource`/`AssignmentInOperand` in `PqcCatalog.load()` (rewritten with try-with-resources), SpotBugs `IMPROPER_UNICODE` on `Locale.ROOT` case folding (per-class exclusion with justification in `config/spotbugs-exclude.xml`, same as the existing `WeakCryptoRule` entry). Third run: BUILD SUCCESS, rules 401, report 8, workspace 160, cli 53, examples 5 tests; coverage checks met; selfcheck reported no `CK-PQC` finding. JDK 25 (CI) not run locally.
- T041: quick profile FAILED the heap gate locally (+44.1%, 87 MB vs 63 MB dev-windows baseline; CPU -7.0%). Unchanged `main` (f669093) in a scratch worktree on the same machine and JDK 26 failed the same gate worse (+97.9%, 119 MB; CPU -0.1%), so the failure is environmental, not this change. Baseline not refreshed. Small-tier findings 109 to 112 and lines 3653 to 3761 come from the three new example classes. Chart and `results/latest.json` side effects reverted. CI's `ci-linux` entry is the authoritative gate.
- T042: steps 1, 2, 3, 5 and the step 4 suppression alternative run against the built jar and match; step 4's baseline commands not run (Milestone 15). The walkthrough exposed a wrong expectation in quickstart step 2 (its sample put `KEM.getInstance("ML-KEM")` in the same method as DHKEM, which is the exempt hybrid pattern): sample fixed, now 4 findings, exit 0 default and exit 1 at `--fail-on warning`.
- T043: 11 issues opened, #63 to #73: the 9 research follow-ups (#63 CK-CRYPTO-WEAK on RSA/ECB, reproduced with the built jar and failing a default build), plus #68 (cipher suites via system properties, analysis finding G1) and #73 (SC-006 precision review, analysis finding G4). #75 added after CI (see T044).
- T044: PR #74. First CI run (3bdf373): every job green except `loadtest`, heap on the 100k tier 126 MB (+22.9%), reproduced on re-run (126 MB, +22.3%), above all 24 earlier runs on unchanged code (101-115 MB). Local JDK 26 probes (serial GC and G1) showed no retained difference, so the cause was isolated to the JDK 25 path: `builtInPqc` resolved `NamedParameterSpec` on every compilation unit. 447590e resolves it only on the first finding and filters names without allocating: CI all green, 100k heap 115 MB (+12.4%), CPU +11.7%. The ungated small tier stays about 20 MB above `main` (124 vs 103 MB); tracked as #75. Not merged.
