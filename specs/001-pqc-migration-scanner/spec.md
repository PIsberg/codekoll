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

Two facts shape the scope and were checked, not assumed:

- **The replacement is not a drop-in.** On the local JDK 26, ML-KEM and ML-DSA are available
  from the platform itself (ML-KEM through the key-encapsulation API, 1088-byte encapsulation;
  ML-DSA through the signature API, 3309-byte signature), but requesting ML-KEM as a cipher
  fails with "no such algorithm". Key encapsulation is a different operation from RSA
  encryption, and signatures and keys are several times larger. A mechanical rename of the
  algorithm string does not produce working code, and a migrated endpoint cannot talk to a
  peer that has not migrated.
- **Codekoll's SPEC.md section 1 lists automatic fixing / rewriting as a v1 non-goal.** Any
  auto-refactoring in this feature reverses that decision, which is why remediation scope is
  a clarification rather than an assumption.

The urgency differs by use. Encrypted traffic and stored ciphertext can be recorded now and
decrypted once a quantum computer exists ("harvest now, decrypt later"), so key establishment
and key transport are exposed today. Signatures only need to resist forgery at the moment they
are verified, so their deadline is later. The tool keeps these two roles apart so teams can
fix confidentiality first.

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
2. **Given** a baseline written from that tree, **When** a developer adds one new ECDSA signature request and CI runs with the warning threshold and the baseline, **Then** exactly one finding is reported and the run fails.
3. **Given** a project configuration that raises the pack's severity to error, **When** the analyzer runs with default settings, **Then** the findings are reported as errors and the run fails.
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

### User Story 4 - Auto-refactoring (Priority: P3)

[NEEDS CLARIFICATION: remediation scope, see Question 1 in the specify report. Guidance only, generated migration recipes for review, or in-place rewrite?] This story is kept or dropped
based on that answer; its acceptance scenarios are written once scope is settled.

---

### Edge Cases

- **Algorithm name not known at analysis time** (read from configuration, a method parameter or a non-constant field): not reported. The precision policy prefers a missed site over a guess. The gap is documented in the rule's explanation.
- **Case and spelling variants** (`rsa`, `RSA/ECB/PKCS1Padding`, `SHA256withECDSA`, `SHA256WITHECDSA`, `RSASSA-PSS`, `Ed25519`, `XDH`, `ECDH`): matched case-insensitively, including full transformation strings and provider-qualified requests.
- **Key pair generation and key factories whose role is ambiguous** (an `EC` or `RSA` key pair can serve either signatures or key establishment): reported without a role claim, at the lower severity in FR-004, so one signing flow does not produce two equal-weight findings.
- **Hybrid usage**: a method that requests a classical key agreement *and* ML-KEM is following the recommended transition pattern; the classical request in that method is exempt.
- **Test sources**: tests routinely generate throwaway RSA or EC keys. Sites in test sources are exempt by default, following the existing test-source precedent in `CK-INSECURE-RANDOM`.
- **Overlap with `CK-CRYPTO-WEAK`**: that rule's code treats any `<alg>/ECB/...` cipher transformation as an ECB-mode error, which appears to include `RSA/ECB/...`, where ECB is a naming quirk (read in `WeakCryptoRule.java`, not reproduced). That behaviour is out of scope here, the new pack must not add a *second* post-quantum finding to one site, and the overlap is recorded as a follow-up issue.
- **Classically weak key sizes** (1024-bit RSA): out of scope for this pack; a quantum-safety finding is reported regardless of key size.
- **Code that does not compile / unresolved types**: follows codekoll's existing behaviour (file reported as skipped); no special handling.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide the quantum-vulnerability rules as a distinct rule pack, so a team can enable, disable or re-severity the whole pack in one setting without affecting the existing `security` pack.
- **FR-002**: The system MUST report a request for a quantum-vulnerable asymmetric algorithm when the algorithm name is a string literal or compile-time constant, for these request kinds: asymmetric encryption / key transport (RSA), digital signature (RSA, RSASSA-PSS, ECDSA, DSA, EdDSA, Ed25519, Ed448), key agreement (DH, ECDH, XDH, X25519, X448), and key pair generation / key factory for any of those algorithm families.
- **FR-003**: Each finding MUST state the algorithm requested, its role (key establishment, signature, or unspecified key material), and the post-quantum replacement for that role (ML-KEM for key establishment, ML-DSA for signatures).
- **FR-004**: Findings MUST default to WARNING severity for key establishment and signature requests, and INFO for key material requests whose role is ambiguous. No rule in the pack may default to ERROR.
- **FR-005**: At the default failure threshold, a run whose only findings come from this pack MUST exit as clean.
- **FR-006**: The system MUST NOT report: post-quantum algorithms (ML-KEM, ML-DSA, SLH-DSA and their parameter-set names), symmetric ciphers, hashes, MACs, classical key agreement in a method that also requests ML-KEM, or any site in test sources.
- **FR-007**: The system MUST produce at most one finding from this pack per request site.
- **FR-008**: Key establishment findings and signature findings MUST carry different rule ids, so they can be filtered, baselined and re-severitied independently.
- **FR-009**: Every rule in the pack MUST work with the existing suppression, baseline, severity-override and output-format mechanisms, with no pack-specific behaviour.
- **FR-010**: Every rule in the pack MUST carry explanation and fix guidance covering: why the algorithm is quantum-vulnerable, urgency for its role, the replacement, why the replacement is not a drop-in, and the hybrid transition pattern.
- **FR-011**: The guidance MUST name where the replacement is available. [NEEDS CLARIFICATION: remediation target, see Question 2. Platform-provided algorithms, Bouncy Castle PQC, or both chosen by target Java release?]
- **FR-012**: Every rule in the pack MUST have a vulnerable and a migrated example in the examples module, and positive and negative fixtures covering every exemption in FR-006.
- **FR-013**: The generated rule catalog and the pack table in the user-facing docs MUST include the new pack and rules.
- **FR-014**: The pack MUST be [NEEDS CLARIFICATION: default enablement, see Question 3. Enabled by default at warning severity, or opt-in?].
- **FR-015**: Remediation MUST be limited to the scope chosen for User Story 4 (Question 1); anything beyond guidance also reverses the SPEC.md section 1 non-goal, and SPEC.md must be updated in the same change.

### Key Entities

- **Vulnerable request site**: one place in source where code asks for a quantum-vulnerable algorithm. Attributes: file, line, column, request kind, algorithm name as written, resolved algorithm family, role.
- **Algorithm family**: the classical family a requested name belongs to (RSA, EC, DSA, EdDSA, DH, XDH) and whether it is quantum-vulnerable.
- **Role**: key establishment (confidentiality, harvest-now-decrypt-later exposure), signature (integrity and authenticity), or unspecified key material.
- **Replacement mapping**: role to post-quantum standard (key establishment to ML-KEM, FIPS 203; signature to ML-DSA, FIPS 204), plus the transition pattern and availability note.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a reference sample containing every request form in FR-002, 100% of vulnerable sites are reported with the correct role, and 0 findings are reported on the exempt forms in FR-006.
- **SC-002**: Adding the pack to a project that previously exited clean changes the exit status of 0 runs at the default threshold.
- **SC-003**: A security engineer can produce a per-role count of vulnerable sites (key establishment vs signature) for a codebase from a single run's machine-readable output, without reading source.
- **SC-004**: After a baseline is written, introducing one new vulnerable request produces exactly 1 reported finding under the warning threshold.
- **SC-005**: Analysis time on the existing load-test corpus rises by no more than 5% with the pack enabled.
- **SC-006**: On at least 2 open-source codebases that use asymmetric cryptography, a manual review of every finding from the pack classifies at least 95% as genuine quantum-vulnerable requests.

## Assumptions

- **Delivery vehicle**: the feature ships as a new rule pack inside codekoll, not as a separate tool. The description allowed either ("a CLI tool or static analysis rule engine"), and codekoll already provides the CLI, output formats, baseline, suppression and configuration this feature needs.
- **Detection scope for v1** is the platform's standard cryptography request APIs with names known at analysis time, the same shape `CK-CRYPTO-WEAK` already detects. Out of scope for v1, each to become a tracked follow-up: TLS configuration (cipher suites, named groups), certificates and keystores, third-party lightweight crypto APIs called directly (for example Bouncy Castle engine and signer classes), and algorithm names that only exist at runtime.
- **Severity**: warnings, as requested. Key establishment and signatures share the WARNING level; urgency is conveyed by separate rule ids and guidance, not by making key establishment an error.
- **Standards referenced**: FIPS 203 (ML-KEM) and FIPS 204 (ML-DSA). Timeline guidance, if quoted in rule text, cites NIST IR 8547 and must be re-checked against its current revision at implementation time; it was read, not verified here.
- **Platform availability**: ML-KEM and ML-DSA were verified to work with the platform's built-in providers on JDK 26 only. Availability on JDK 25, codekoll's build target, is believed but not verified and must be checked before guidance text claims it.
- **"Unit tests to prove backward compatibility"** from the description is not carried as a requirement: ML-KEM and ML-DSA are wire-incompatible with RSA and ECDSA by design, so no test can prove a migrated site backward compatible. Compatibility during transition comes from the hybrid pattern in FR-010.
- **Test source detection** reuses whatever mechanism codekoll already uses to exempt test sources.
