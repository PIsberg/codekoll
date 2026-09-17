# Feature Specification: PQC Migration Scanner & Static Guardrail

**Feature Branch**: `001-pqc-migration-scanner`

**Created**: 2026-09-17

**Status**: Draft

**Input**: User description: "Java PQC Migration Scanner & Static Guardrail. A CLI tool or static analysis rule engine that audits enterprise Java codebases for quantum-vulnerable cryptography (RSA, ECDSA, standard Diffie-Hellman) and suggests/auto-refactors them to NIST Post-Quantum standards (ML-KEM/Kyber, ML-DSA/Dilithium) using Bouncy Castle PQC. LLMs excel at AST analysis, pattern-matching cryptographic APIs (e.g., Cipher.getInstance("RSA")), and generating targeted OpenRewrite recipes or unit tests to prove backward compatibility. Perhaps these should come as warnings as of now and not errors."

## Context

Codekoll already analyzes Java source for bugs that compile cleanly and fail at runtime, and
already ships a `security` pack whose `CK-CRYPTO-WEAK` rule flags *classically* broken
algorithms (MD5, SHA-1, DES, RC4). This feature covers a different threat: asymmetric
algorithms that are sound today but fall to a cryptographically relevant quantum computer
(Shor's algorithm). Symmetric ciphers and hashes at current strengths are not in that class.

Three facts shape the scope and were checked, not assumed:

- **The replacement is not a drop-in.** On the local JDK 26, ML-KEM and ML-DSA are available
  from the platform itself (ML-KEM through the key-encapsulation API, 1088-byte encapsulation;
  ML-DSA through the signature API, 3309-byte signature), but requesting ML-KEM as a cipher
  fails with "no such algorithm". Key encapsulation is a different operation from RSA
  encryption, and signatures and keys are several times larger. A mechanical rename of the
  algorithm string does not produce working code, and a migrated endpoint cannot talk to a
  peer that has not migrated.
- **Codekoll's SPEC.md section 1 lists automatic fixing / rewriting as a v1 non-goal.** This
  feature keeps that non-goal: remediation is guidance only (see Clarifications).
- **"Post-quantum-sounding" is not post-quantum.** JDK 21+ ships a key-encapsulation algorithm
  named `DHKEM`, and JDK 26 ships an `HPKE` cipher whose only key-encapsulation options are
  DHKEM variants. Both rest on classical Diffie-Hellman and are quantum-vulnerable. Coverage
  must follow what the algorithm is, not what the API is called.

The urgency differs by use. Encrypted traffic and stored ciphertext can be recorded now and
decrypted once a quantum computer exists ("harvest now, decrypt later"), so key establishment
and key transport are exposed today. Signatures only need to resist forgery at the moment they
are verified, so their deadline is later. The tool keeps these two roles apart so teams can
fix confidentiality first.

## Clarifications

### Session 2026-09-17

- Q: How wide is "all of Java's vulnerable algorithms"? → A: The whole JDK. Every quantum-vulnerable algorithm the JDK's providers expose through every cryptographic request API (names, aliases, OIDs, parameter specifications), plus the JDK's TLS configuration strings (cipher suites, named groups, signature schemes) and XML signature method identifiers. Coverage is checked against the running JDK so a newly added algorithm cannot go unclassified.
- Q: How far does remediation go? → A: Guidance only (finding message, rule explanation, example pairs). The tool never modifies source; the SPEC.md non-goal stands.
- Q: Where does guidance point for ML-KEM / ML-DSA? → A: By the project's target Java release: the platform's built-in providers where that release has them, Bouncy Castle PQC for older releases.
- Q: Is the pack enabled by default? → A: Yes, at WARNING severity (INFO for ambiguous key material).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Inventory quantum-vulnerable cryptography (Priority: P1)

A security engineer points codekoll at an enterprise Java source tree and gets one finding per
place where the code requests a quantum-vulnerable asymmetric algorithm: RSA encryption, RSA,
ECDSA, DSA or EdDSA signatures, and Diffie-Hellman or elliptic-curve key agreement. Each
finding names the algorithm requested, its role (key establishment or signature), and the NIST
post-quantum replacement for that role (ML-KEM for key establishment, ML-DSA for signatures).

**Why this priority**: A migration cannot be planned without an inventory, and today
enterprises build that inventory by grep. This alone is a shippable product: the finding list
is the migration backlog.

**Independent Test**: Run the analyzer on a sample tree containing one example of every
vulnerable request form listed in FR-002 and one example of each exempt form in FR-006. Every
vulnerable site produces exactly one finding with the correct role and replacement; no exempt
site produces a finding.

**Acceptance Scenarios**:

1. **Given** a class that requests RSA with OAEP padding for encryption, **When** the analyzer runs, **Then** one finding reports RSA used for key transport, classifies it as key establishment, and names ML-KEM as the replacement.
2. **Given** a class that requests an ECDSA-with-SHA-256 signature, **When** the analyzer runs, **Then** one finding reports ECDSA, classifies it as signature, and names ML-DSA as the replacement.
3. **Given** a class that requests X25519 or classic Diffie-Hellman key agreement, **When** the analyzer runs, **Then** one finding reports it as key establishment with ML-KEM as the replacement.
4. **Given** a class that requests only ML-KEM, ML-DSA, AES-GCM and SHA-256, **When** the analyzer runs, **Then** the post-quantum pack reports nothing for that class.
5. **Given** an algorithm name held in a compile-time constant rather than written inline, **When** the analyzer runs, **Then** the finding is reported at the request site exactly as for an inline name.

---

### User Story 2 - Guardrail that warns, not blocks (Priority: P1)

A platform team adds the scan to CI. Existing quantum-vulnerable usage is reported as warnings
and does not fail the build at the default threshold. Teams that want a hard gate on *new*
vulnerable usage record the current findings as a baseline and raise the threshold, so the
build fails only when someone adds a new RSA or ECDSA request.

**Why this priority**: The request was for warnings, not errors. Nearly every enterprise
codebase uses RSA or ECDSA today, often legitimately, because peers, certificates and hardware
tokens have not migrated. A rule that breaks every build on day one gets disabled, which is
worse than having no rule.

**Independent Test**: Run the analyzer at the default threshold on a tree with vulnerable
usage and confirm the exit status signals success while the findings still appear in console,
JSON and SARIF output. Then write a baseline, add one new vulnerable request, run with the
warning threshold and the baseline, and confirm only the new site is reported and the run
fails.

**Acceptance Scenarios**:

1. **Given** a tree with 20 quantum-vulnerable request sites and no other findings, **When** the analyzer runs with default settings, **Then** all 20 findings are reported at warning or info severity (per FR-004) and the run exits as clean.
2. **Given** a baseline written from that tree, **When** a developer adds one new ECDSA signature request and CI runs with the warning threshold and the baseline, **Then** exactly one finding is reported and the run fails. *(Depends on codekoll's baseline support, planned as Milestone 15 and not implemented today; see Dependencies. Until then the pack's value in CI is the warning inventory plus scenarios 3 and 4.)*
3. **Given** a project configuration that raises a pack rule's severity to error, **When** the analyzer runs with default settings, **Then** the findings are reported as errors and the run fails.
4. **Given** a single legitimate RSA site (for example, verifying a partner's signature that cannot change yet), **When** a developer suppresses that finding inline, **Then** that site is no longer reported and all other sites still are.

---

### User Story 3 - Migration guidance a developer can act on (Priority: P2)

A developer who receives a finding asks the tool to explain the rule and gets: why the
algorithm is quantum-vulnerable, why key establishment is more urgent than signatures, what the
replacement is, why the replacement is not a drop-in (different operation, larger keys and
signatures, peer interoperability), the recommended transition pattern (hybrid: classical and
post-quantum combined until peers migrate), and where the replacement is available for the
Java release the project targets.

**Why this priority**: An inventory without guidance becomes a ticket nobody can start.
Codekoll's rules already carry what-is-wrong / what-happens / how-to-fix metadata; this story
holds the new rules to that standard. It ranks below P1 because the inventory is useful on its
own.

**Independent Test**: For each new rule, request its explanation and confirm it covers the six
points above; confirm the examples module contains a vulnerable and a migrated variant for each
rule and that the analyzer flags the first and stays silent on the second.

**Acceptance Scenarios**:

1. **Given** any new rule id, **When** the developer asks for its explanation, **Then** the output states the vulnerable role, the replacement, the non-drop-in caveat and the hybrid transition pattern.
2. **Given** the examples module, **When** the analyzer runs on it, **Then** every new rule fires on its vulnerable example and not on its migrated example.

---

### User Story 4 - Coverage that stays complete as Java evolves (Priority: P2)

A codekoll maintainer upgrades the JDK the project builds on. If the new JDK exposes an
asymmetric or key-establishment algorithm name, alias or OID that the pack has never
classified, the build fails and names it, so the catalog is extended deliberately instead of
silently missing the new algorithm.

**Why this priority**: "Cover all of Java's vulnerable algorithms" is only true on the day it
is written unless something checks it. Between JDK 8 and JDK 26 the platform added 84
signature, key-agreement, key-factory, key-pair-generator and KEM entries in these categories
(58 classical, 26 post-quantum; counted from the providers of JDK 8 and JDK 26 on this machine), including `DHKEM`, `HPKE`,
`XDH` and the ML-KEM / ML-DSA families.

**Independent Test**: Run the completeness check on the build JDK and confirm it passes; then
remove one classified name (for example `SHA3-256withECDSA`) from the catalog and confirm the
check fails naming that algorithm.

**Acceptance Scenarios**:

1. **Given** the build JDK's providers, **When** the completeness check runs, **Then** every algorithm name, alias and OID of every covered request type is classified as vulnerable, safe, or out of scope, and the check passes.
2. **Given** a catalog missing one name the JDK exposes, **When** the check runs, **Then** it fails and names the missing algorithm and its request type.

---

### Edge Cases

- **Names only known at runtime** (read from configuration, a method parameter or a non-constant field): not reported. The precision policy prefers a missed site over a guess. The gap is documented in the rule's explanation.
- **Case, alias and OID variants** (`rsa`, `RSA/ECB/PKCS1Padding`, `SHA256WITHECDSA`, `DH` for `DiffieHellman`, `PSS` for `RSASSA-PSS`, `DSS` for `SHA1withDSA`, `1.2.840.10045.4.3.2` and `OID.1.2.840.10045.4.3.2` for `SHA256withECDSA`): matched case-insensitively, including full transformation strings and provider-qualified requests.
- **Platform-specific providers** (the Windows key-store provider offers its own RSA and ECDSA services): same names, same findings.
- **HPKE**: on JDK 26 every key-encapsulation option for HPKE is a classical DHKEM, so an HPKE request is reported as key establishment. A future non-classical HPKE key-encapsulation option is exempt once it exists.
- **TLS 1.3 cipher suites** (`TLS_AES_256_GCM_SHA384`) do not name a key exchange and are not reported; the key exchange is governed by named groups, which are. TLS 1.2 suites that name RSA, DHE or ECDHE key exchange are reported.
- **Stateful hash-based signatures** (`HSS/LMS`) are quantum-safe and not reported.
- **Key material** (key pair generation, key factories, algorithm parameters, parameter specifications): reported at the lower severity in FR-004, naming the role when the family implies one (`X25519` is key establishment, `Ed25519` is signature) and none when it does not (`EC`, `RSA`). A signing flow therefore produces one WARNING for the signature and INFO for the key it uses, never two equal-weight findings. A parameter specification in a method that already reports a key material request for the same key is not reported again.
- **Hybrid usage**: a method that requests a classical key agreement *and* ML-KEM is following the recommended transition pattern; the classical request in that method is exempt.
- **Test sources**: tests routinely generate throwaway RSA or EC keys. Sites in test sources (the build's conventional test source directories) are exempt by default. There is no existing precedent to reuse: SPEC.md describes a test-source exemption for `CK-INSECURE-RANDOM`, but the implementation has none (read in `InsecureRandomRule.java`).
- **Overlap with `CK-CRYPTO-WEAK`**: that rule's code treats any `<alg>/ECB/...` cipher transformation as an ECB-mode error, which appears to include `RSA/ECB/...`, where ECB is a naming quirk (read in `WeakCryptoRule.java`, not reproduced). That behaviour is out of scope here, the new pack must not add a *second* post-quantum finding to one site, and the overlap is recorded as a follow-up issue.
- **Classically weak key sizes** (1024-bit RSA): out of scope for this pack; a quantum-safety finding is reported regardless of key size.
- **Code that does not compile / unresolved types**: follows codekoll's existing behaviour (file reported as skipped); no special handling.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide the quantum-vulnerability rules as a distinct rule pack, so a team can enable or disable the whole pack in one setting without affecting the existing `security` pack. Severity is overridden per rule id (three settings), because codekoll's `[severity]` table accepts rule ids only (`Settings.java`, verified during implementation).
- **FR-002**: The system MUST report every request for a quantum-vulnerable algorithm whose name is a string literal or compile-time constant, across all of these request kinds: asymmetric encryption and key wrapping (RSA in any transformation), hybrid public-key encryption (HPKE), key encapsulation (DHKEM), digital signature (every RSA, RSASSA-PSS, DSA, ECDSA, EdDSA, Ed25519 and Ed448 variant, including P1363-format and SHA-3 variants), key agreement (DiffieHellman, ECDH, XDH, X25519, X448), key pair generation, key factories, and algorithm parameter generation or parameters for DSA, DiffieHellman, EC and RSASSA-PSS. Names, aliases and OIDs the JDK accepts are all covered.
- **FR-002a**: The system MUST report quantum-vulnerable parameter selections made through the platform's parameter-specification types: named curves and named parameter constants (X25519, X448, Ed25519, Ed448, any EC curve name), and RSA, DSA and Diffie-Hellman key-generation parameters.
- **FR-002b**: The system MUST report quantum-vulnerable TLS configuration set in code: named groups (all finite-field and elliptic-curve groups), signature schemes (RSA, RSA-PSS, DSA, ECDSA, EdDSA), and TLS 1.2 cipher suites naming RSA, DHE or ECDHE key exchange, whether set through the TLS parameter APIs or through the platform's TLS system properties.
- **FR-002c**: The system MUST report XML digital signature method selections naming RSA, RSA-PSS, DSA, ECDSA or EdDSA, whether through the platform's signature-method constants or the equivalent URI strings.
- **FR-003**: Each finding MUST state the algorithm requested, its role (key establishment, signature, or unspecified key material), and the post-quantum replacement for that role (ML-KEM for key establishment, ML-DSA for signatures).
- **FR-004**: Findings MUST default to WARNING severity for key establishment and signature requests, and INFO for key material (key pair generation, key factories, algorithm parameters, parameter specifications). No rule in the pack may default to ERROR.
- **FR-005**: At the default failure threshold, a run whose only findings come from this pack MUST exit as clean.
- **FR-006**: The system MUST NOT report: post-quantum algorithms (ML-KEM, ML-DSA, SLH-DSA, HSS/LMS and their parameter-set names and OIDs), symmetric ciphers, hashes, MACs, password-based schemes, TLS 1.3 cipher suites, classical key agreement in a method that also requests ML-KEM, or any site in test sources.
- **FR-007**: The system MUST produce at most one finding from this pack per request site.
- **FR-008**: Key establishment findings and signature findings MUST carry different rule ids, so they can be filtered, baselined and re-severitied independently.
- **FR-009**: Every rule in the pack MUST work with the existing suppression, severity-override, pack-selection and output-format mechanisms, and with baselines once they exist, with no pack-specific behaviour.
- **FR-010**: Every rule in the pack MUST carry explanation and fix guidance covering: why the algorithm is quantum-vulnerable, urgency for its role, the replacement, why the replacement is not a drop-in, and the hybrid transition pattern.
- **FR-011**: The guidance MUST name where the replacement is available for the analyzed project's target Java release: the platform's built-in ML-KEM and ML-DSA for release 24 and later, Bouncy Castle PQC (or an upgrade to release 24) for earlier releases. When the target release cannot be detected, guidance follows the release codekoll already assumes and announces for that case.
- **FR-012**: Every rule in the pack MUST have a vulnerable and a migrated example in the examples module, and positive and negative fixtures covering every exemption in FR-006.
- **FR-013**: The generated rule catalog and the pack table in the user-facing docs MUST include the new pack and rules.
- **FR-014**: The pack MUST be enabled by default.
- **FR-015**: Remediation MUST be guidance only. The system MUST NOT modify analyzed source.
- **FR-016**: The build MUST fail when the JDK it runs on exposes an algorithm name, alias or OID, for any request type in FR-002, that the pack's catalog does not classify as vulnerable, safe or out of scope.

### Key Entities

- **Vulnerable request site**: one place in source where code asks for a quantum-vulnerable algorithm. Attributes: file, line, column, request kind, algorithm name as written, resolved algorithm family, role.
- **Algorithm family**: the classical family a requested name belongs to (RSA, EC, DSA, EdDSA, DH, XDH) and whether it is quantum-vulnerable.
- **Role**: key establishment (confidentiality, harvest-now-decrypt-later exposure), signature (integrity and authenticity), or unspecified key material.
- **Replacement mapping**: role to post-quantum standard (key establishment to ML-KEM, FIPS 203; signature to ML-DSA, FIPS 204), plus the transition pattern and availability note.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a reference sample containing every request form in FR-002 to FR-002c, 100% of vulnerable sites are reported with the correct role, and 0 findings are reported on the exempt forms in FR-006.
- **SC-001a**: 100% of the relevant algorithm names, aliases and OIDs exposed by the build JDK are classified (FR-016), with 0 unclassified.
- **SC-002**: Adding the pack to a project that previously exited clean changes the exit status of 0 runs at the default threshold.
- **SC-003**: A security engineer can produce a per-role count of vulnerable sites (key establishment vs signature) for a codebase from a single run's machine-readable output, without reading source.
- **SC-004**: After a baseline is written, introducing one new vulnerable request produces exactly 1 reported finding under the warning threshold. Measurable once baseline support (Milestone 15) lands; not a release gate for this feature.
- **SC-005**: Analysis time on the existing load-test corpus rises by no more than 5% with the pack enabled.
- **SC-006**: On at least 2 open-source codebases that use asymmetric cryptography, a manual review of every finding from the pack classifies at least 95% as genuine quantum-vulnerable requests.

## Dependencies

- **Element-level suppression** (`@SuppressWarnings("codekoll:<id>")`) is specified in SPEC.md section 3.4 but not implemented (`SuppressionFilter.java` handles only the `codekoll:off` line comment). User Story 2 scenario 4 is satisfied by the line comment.
- **Baseline support** (`--baseline`, `--write-baseline`) is specified in `docs/CLI-SPEC.md` section 8 and scheduled as Milestone 15, but not implemented: `Settings.java` rejects `report.baseline` naming that milestone (read during planning). This feature does not implement it. User Story 2 scenario 2 and SC-004 become testable when it lands.

## Assumptions

- **Provider-specific names outside the JDK** (for example algorithm names only a Bouncy Castle provider registers, such as ECIES or ElGamal, requested through the standard request APIs) are out of scope with the other third-party coverage.
- **Delivery vehicle**: the feature ships as a new rule pack inside codekoll, not as a separate tool. The description allowed either ("a CLI tool or static analysis rule engine"), and codekoll already provides the CLI, output formats, line-comment suppression and configuration this feature needs (baselines and element-level suppression do not exist yet; see Dependencies).
- **Detection scope for v1** is everything the JDK itself defines (Clarifications). Out of scope, each to become a tracked follow-up: third-party libraries (Bouncy Castle lightweight classes, JOSE/JWT libraries), algorithms carried inside certificates and keystores (a PKI decision, not a code site), code that merely handles an existing RSA or EC key object without requesting an algorithm, and names that only exist at runtime.
- **Severity**: warnings, as requested. Key establishment and signatures share the WARNING level; urgency is conveyed by separate rule ids and guidance, not by making key establishment an error.
- **Standards referenced**: FIPS 203 (ML-KEM) and FIPS 204 (ML-DSA). Rule text quotes no NIST IR 8547 dates (research R9).
- **Platform availability**: ML-KEM and ML-DSA were exercised end to end on JDK 26 only. Their API constants compile against `--release 24` and `--release 25` and fail against 21 to 23 (checked with JDK 26's compiler), which places the built-in support at release 24. The providers of JDK 8, 11, 17 and 21 on this machine expose no ML-KEM or ML-DSA service.
- **"Unit tests to prove backward compatibility"** from the description is not carried as a requirement: ML-KEM and ML-DSA are wire-incompatible with RSA and ECDSA by design, so no test can prove a migrated site backward compatible. Compatibility during transition comes from the hybrid pattern in FR-010.
- **Test source detection** is by conventional test source directory, because analysis rules currently receive no test-source signal from workspace discovery.
- **HPKE parameter selections** (choosing a DHKEM through HPKE's parameter type) exist only on JDK 26. Codekoll builds and tests on JDK 25, where fixtures for them cannot compile, so they are a follow-up; requesting the HPKE cipher by name is covered.
