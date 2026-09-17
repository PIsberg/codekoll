# Research: PQC Migration Scanner & Static Guardrail

**Feature**: `001-pqc-migration-scanner` | **Date**: 2026-09-17 | **Spec**: [spec.md](spec.md)

Every decision below records how its facts were obtained. **Verified** means reproduced on this
machine during planning; **read** means taken from source or documentation without running it.

## R1. What "all of Java's vulnerable algorithms" means, concretely

**Decision**: The catalog is derived from the JDK's own provider registry, not from a hand-written
list. Eight request types are covered: `Signature`, `KeyPairGenerator`, `KeyAgreement`,
`KeyFactory`, `KEM`, `Cipher`, `AlgorithmParameters`, `AlgorithmParameterGenerator`. For each,
every service name and every `Alg.Alias.*` entry (plain aliases, OIDs, `OID.`-prefixed OIDs) is
classified as `VULNERABLE` (with a role), `PQ_SAFE`, or `NOT_ASYMMETRIC`.

**Verified**: A probe program enumerated `Security.getProviders()` services and alias properties on
JDK 8.0_202, 11.0.16, 17.0.5, 21 and 26 (JDK 24 on this machine is a dangling install; JDK 25 is
not installed). JDK 26 on Windows yields 122 asymmetric or post-quantum service entries in the
eight types: 96 vulnerable, 26 post-quantum-safe. The full table is in the appendix.

Facts the probe surfaced that a hand-written list would likely miss:

- `KEM` / `DHKEM` (JDK 21+) is Diffie-Hellman based, so quantum-vulnerable despite the KEM API.
- `Cipher` / `HPKE` (JDK 26 only). `javap` on `javax.crypto.spec.HPKEParameterSpec` shows its only
  KEM ids are `KEM_DHKEM_P_256/P_384/P_521/X25519/X448`: every HPKE configuration on JDK 26 is
  quantum-vulnerable.
- `Signature` / `HSS/LMS` (JDK 24+ per the diff between 21 and 26) is hash-based, quantum-safe.
- 20 `*inP1363Format` and 12 `SHA3-*with{RSA,DSA,ECDSA}` signature variants, and `MD5andSHA1withRSA`
  registered by SunJSSE.
- Aliases that do not contain the family name: `DSS`, `RawDSA`, `SHAwithDSA`, `SHA/DSA` (all DSA),
  `PSS` (RSASSA-PSS), `EllipticCurve` (EC), `DH` (DiffieHellman), and OIDs such as
  `1.3.14.3.2.29` (SHA1withRSA) and `1.3.101.110` (X25519).
- SunMSCAPI (Windows only) registers its own `RSA`, `RSA/ECB/PKCS1Padding`, `SHA*withRSA` and
  `SHA*withECDSA` services under the same names.
- `Cipher` `RSA` is one service; transformations such as `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` are
  resolved through the service's supported modes, so matching is on the first transformation
  segment.

**Rationale**: The user asked for complete coverage. A registry-derived catalog plus a completeness
test (R2) is the only form of "complete" that stays true after a JDK upgrade.

**Alternatives considered**: Family-name regex over the requested string (`.*RSA.*`): rejected,
misses `DSS`, `RawDSA`, `EllipticCurve`, `DH`, every OID, and would flag unrelated names such as
`SunTlsRsaPremasterSecret`. Hand-maintained list from the JDK Standard Algorithm Names document:
rejected as the primary source because it has no enforcement, but useful when classifying a new
name the completeness test reports.

## R2. Keeping coverage complete (FR-016)

**Decision**: A unit test in `codekoll-rules` enumerates the running JDK's providers (same method as
the R1 probe) and fails, listing type and name, for any service name or alias in the eight types
that the catalog does not classify. `NOT_ASYMMETRIC` is expressed as an explicit family-prefix
list (AES, ARCFOUR, Blowfish, ChaCha20, DES, DESede, GCM, OAEP, PBE, PBES2, PBEWith, RC2, RC4)
rather than per-name entries, so symmetric additions such as a new `AES_*` mode classify without
edits while a genuinely new family still fails. The same test also enumerates the TLS named
groups, TLS signature schemes, TLS cipher suites and `javax.xml.crypto.dsig.SignatureMethod`
string constants of the running JDK and requires each to be classified.

**Verified**: the enumeration technique works on JDK 8 through 26 (R1). `SSLParameters.getNamedGroups`
and `getSignatureSchemes` do not exist on 8, 11 or 17; on 21 `getNamedGroups` returns 10 groups and
`getSignatureSchemes` returns null (provider default); on 26 both return lists. The test must use
the JDK 25 API directly (CI runs Temurin 25), and on a null signature-scheme list fall back to the
catalog's own list rather than pass vacuously.

**Rationale**: Green must mean "every name was looked at". A catalog that silently lacks
`SHA3-512withECDSAinP1363Format` passes every fixture test. This test is what fails.

**What would make it fail**: a JDK upgrade adding a name; a catalog edit dropping one. Deliberate
check during implementation: delete one catalog entry, confirm red naming it, restore.

**Alternatives considered**: Snapshot file of the registry committed to the repo: rejected, it
records the list but does not force classification. Running the check only in the load-test or
examples module: rejected, it belongs next to the catalog so `mvn test` on `codekoll-rules` fails.

## R3. Rule split and severities

**Decision**: Three rules in a new pack `pqc`:

| Rule id | Default | Covers |
|---|---|---|
| `CK-PQC-KEY-EXCHANGE` | WARNING | `KeyAgreement`, `KEM` DHKEM, `Cipher` RSA (any transformation) and HPKE, TLS named groups, TLS 1.2 cipher suites with RSA/DH/DHE/ECDH/ECDHE key exchange |
| `CK-PQC-SIGNATURE` | WARNING | `Signature`, XML DSig signature methods, TLS signature schemes |
| `CK-PQC-KEY-MATERIAL` | INFO | `KeyPairGenerator`, `KeyFactory`, `AlgorithmParameters`, `AlgorithmParameterGenerator` for vulnerable families; parameter specifications (`ECGenParameterSpec`, `ECParameterSpec`, `RSAKeyGenParameterSpec`, `DHParameterSpec`, `DHGenParameterSpec`, `DSAParameterSpec`, `NamedParameterSpec` X25519/X448/ED25519/ED448 constants and string constructor) |

**Read** (in `codekoll-rules/.../support/RuleContext.java:64-75` and
`codekoll-examples/.../ExampleVerificationTest.java:129`): `RuleContext.report` always stamps
`rule.defaultSeverity()`, and the examples test asserts it. One rule cannot emit both WARNING and
INFO, so severity dictates the split into separate rules. FR-008 dictates separate ids for key
establishment and signatures.

**Rationale**: Three rules is the minimum that satisfies FR-004 and FR-008. `AbstractRule.scan` runs
one full `TreePathScanner` per rule (read, `AbstractRule.java:15-27`; the Tree.Kind subscription
SPEC.md describes is not implemented), so each rule costs a traversal; fewer rules protects SC-005.

**Alternatives considered**: A separate TLS rule: rejected, it would mix key-establishment and
signature findings under one id (violates FR-008). One rule per request type (8+ rules): rejected
for traversal cost and noise. Reporting key material at the role's WARNING level: rejected, a
signing flow would yield two equal-weight warnings for one migration decision.

## R4. Matching request sites

**Decision**: A shared package-private matcher resolves, for a method invocation or `new`
expression: the receiver or class `TypeElement` qualified name, the method name, and the relevant
argument's compile-time constant string (literal, or identifier / member select whose
`VariableElement.getConstantValue()` is a `String`, the same resolution `WeakCryptoRule` uses at
`WeakCryptoRule.java:106-118`). Names are normalised case-insensitively; `Cipher` transformations
are split on `/` and the first segment classified. Provider arguments (`getInstance(name, "BC")`,
`getInstance(name, provider)`) do not change classification.

Site table:

| Site | Rule |
|---|---|
| `javax.crypto.KeyAgreement.getInstance(n, ...)` | KEY-EXCHANGE |
| `javax.crypto.KEM.getInstance(n, ...)` | KEY-EXCHANGE |
| `javax.crypto.Cipher.getInstance(t, ...)` | KEY-EXCHANGE |
| `java.security.Signature.getInstance(n, ...)` | SIGNATURE |
| `java.security.KeyPairGenerator/KeyFactory/AlgorithmParameters/AlgorithmParameterGenerator.getInstance(n, ...)` | KEY-MATERIAL |
| `new java.security.spec.{ECGenParameterSpec, ECParameterSpec, RSAKeyGenParameterSpec, DSAParameterSpec, NamedParameterSpec}(...)`, `new javax.crypto.spec.{DHParameterSpec, DHGenParameterSpec}(...)` | KEY-MATERIAL |
| field access `java.security.spec.NamedParameterSpec.{X25519, X448, ED25519, ED448}` | KEY-MATERIAL |
| `javax.net.ssl.SSLParameters.setNamedGroups(String[])` | KEY-EXCHANGE |
| `SSLParameters.setCipherSuites`, `SSLSocket/SSLServerSocket/SSLEngine.setEnabledCipherSuites(String[])` | KEY-EXCHANGE (only if an element is a TLS 1.2 suite naming classical key exchange) |
| `SSLParameters.setSignatureSchemes(String[])` | SIGNATURE |
| `System.setProperty` / `java.security.Security.setProperty` with key `jdk.tls.namedGroups` | KEY-EXCHANGE |
| same, key `jdk.tls.client.SignatureSchemes` or `jdk.tls.server.SignatureSchemes` | SIGNATURE |
| `javax.xml.crypto.dsig.XMLSignatureFactory.newSignatureMethod(uri, ...)` | SIGNATURE |

String-array arguments are inspected when they are a `new String[] {...}` initializer, or an
identifier whose declaration in the same compilation unit is a `static final` array initializer;
each element must be a constant. One finding per call site (FR-007), naming the vulnerable
elements. Comma-separated property values are split on `,` and trimmed.

**Exemptions** (each a negative fixture): post-quantum names; `NOT_ASYMMETRIC` names; TLS 1.3 suites
(`TLS_AES_*`, `TLS_CHACHA20_*`) and `TLS_EMPTY_RENEGOTIATION_INFO_SCSV`; non-constant names; a
`KeyAgreement` or `KEM` DHKEM request in a method that also contains an `ML-KEM*` request of any
type (hybrid, FR-006); a parameter specification in a method that already reports a KEY-MATERIAL
`getInstance` (edge case in spec); test sources (R6).

**Verified**: TLS suite, group and scheme names and the `SignatureMethod` URIs are copied from the JDK
26 probe (appendix). **Read, to confirm in the first fixture**: that `SignatureMethod.RSA_SHA256` and
peers resolve through `getConstantValue()` (they are `static final String` interface fields
initialised with literals, which javac treats as constants); that `setNamedGroups` and
`setSignatureSchemes` exist in the JDK 25 API (the probe saw `getNamedGroups` on 21, both on 26).

**Alternatives considered**: Extending `CK-CRYPTO-WEAK`: rejected, different pack, different
severity, different threat; the spec requires a separately switchable pack (FR-001). Flow-tracking
names through local variables: rejected for v1, codekoll rules are method-local and the precision
policy prefers a miss (SPEC.md section 8).

## R5. Release-aware guidance (FR-011) without an SPI change

**Decision**: Decide "does the target platform have ML-KEM / ML-DSA built in" by asking the
compiler's symbol table, not by a version number: `elements.getTypeElement("java.security.spec.NamedParameterSpec")`
and check for enclosed fields `ML_KEM_768` and `ML_DSA_65`. javac resolves platform classes for the
`--release` it was given, so the answer reflects the analyzed project's target release. Result is
computed once per compilation unit. Messages then say either "built into this project's Java
platform (`KEM.getInstance("ML-KEM")`, `Signature.getInstance("ML-DSA")`)" or "not built into this
project's Java release: use Bouncy Castle PQC, or target Java 24+". `fix()` (static metadata used by
`--explain` and SARIF) names both.

**Verified**: with JDK 26's `javac`, a class referencing `NamedParameterSpec.ML_KEM_768` and
`ML_DSA_65` fails with "cannot find symbol" for `--release 21`, `22` and `23` and compiles for `24`
and `25`. ML-KEM (1088-byte encapsulation) and ML-DSA (3309-byte signature) run on JDK 26;
`Cipher.getInstance("ML-KEM")` throws `NoSuchAlgorithmException`.

**Read**: `Rule.scan` receives no release (`codekoll-api/.../Rule.java:34`), but the detected release
is passed to javac (`CompilationDriver.java:47-48`), and when detection fails workspace discovery
falls back to `Runtime.version().feature()` and prints the assumption
(`WorkspaceDiscovery.java:388-391`). The symbol-table probe inherits that behaviour, which is what
FR-011 now says.

**Alternatives considered**: Add `release` to the Rule SPI or `RuleContext`: rejected, touches
`codekoll-api` and all 110 rule call paths for one pack. Check provider services at analysis time:
rejected, that describes the JDK running codekoll, not the project's target.

## R6. Test-source exemption

**Decision**: A support helper decides test source by path: the compilation unit's source path
contains a directory segment `test`, `tests`, `testFixtures`, `integrationTest` or `it` directly
under `src` (Maven and Gradle conventions). The pack's rules skip such units.

**Read**: no rule receives a test-source signal. `WorkspaceDiscovery` marks test source roots
(`WorkspaceDiscovery.java:42-44, 243-258`) and `--no-tests` drops them, but `AnalysisUnit` carries
only name, files, release, classpath and source path. SPEC.md section 6.5 says `CK-INSECURE-RANDOM`
exempts test sources; `InsecureRandomRule.java` has no such check. That is existing doc/code drift,
recorded as a follow-up, not fixed here.

**Rationale**: The path convention covers the layouts workspace discovery itself recognises, with no
SPI change. A fixture harness test can exercise it because the harness names the in-memory file.

**Alternatives considered**: Plumb the workspace's test-root flag through the engine to rules:
correct long-term, but an SPI change across modules; recorded as a follow-up. Do not exempt tests
and rely on `--no-tests`: rejected, the default run includes tests and would bury the inventory in
throwaway test keys.

## R7. Pack placement and wiring

**Decision**: Add `PQC` as the last constant of `RulePack` (id `pqc`, so `--catalog` prints it last);
rules in a new package `io.codekoll.rules.pqc` with `package-info.java`; register each rule in both
`codekoll-rules/src/main/java/module-info.java` and
`codekoll-rules/src/main/resources/META-INF/services/io.codekoll.api.Rule`.

**Read**: `RulePack.id()` lowercases the constant; pack names in config and `--packs` are derived from
loaded rules (`Settings.java:103-137`), so no CLI change is needed and the pack is enabled by default
(FR-014). The two registration lists have no consistency test between them.

## R8. Examples, docs and baselines touched by the change (FR-012, FR-013)

**Read**: three example classes are required by `ExampleVerificationTest` naming
(`CK-PQC-KEY-EXCHANGE` to `PqcKeyExchangeExample`, and so on), each with the three Javadoc sections, a
`buggy()` carrying `// :: CK-PQC-...` markers and a silent `fixed()`. Examples compile at release 25
with an empty classpath, so `fixed()` can use the built-in ML-KEM / ML-DSA APIs but not Bouncy Castle
and not `HPKEParameterSpec` (JDK 26). Hardcoded counts that change from 110 rules / ten packs to 113
rules / eleven packs: `README.md` (badges and lines 35, 39, 49, 86, pack table), `SPEC.md` (lines
115, 199, 201-215, rule index, new section 6.11), `PLAN.md` (3, 21, 24, 51), `docs/RULES.md`
(regenerate with `--catalog`), `docs/CLI-PLAN.md` (117, 219), `docs/CLI-SPEC.md` (129),
`.claude/skills/codekoll/SKILL.md` (11). `codekoll-load-test/baseline.json` records 109 findings for
the small tier; it changes only if the corpus requests vulnerable algorithms, to be measured, not
assumed.

## R9. Standards and wording in guidance

**Decision**: Guidance cites FIPS 203 (ML-KEM) and FIPS 204 (ML-DSA) by number, recommends ML-KEM-768
and ML-DSA-65 as defaults, and recommends hybrid key establishment (classical plus ML-KEM) during
transition. It does not quote dates from NIST IR 8547.

**Read, not verified**: FIPS 203 and 204 numbering and the ML-KEM-768 / ML-DSA-65 default
recommendation are from general knowledge of the NIST standards. IR 8547's deprecation timeline is
omitted because this planning session did not check its current revision; a date in rule metadata
would be shipped to every user and is costly to get wrong.

## Follow-ups to track as issues when the PR opens

Each is out of scope for this feature and needs an issue in this repository, per the delivery rules:

1. Bouncy Castle-only JCA names (`ECIES`, `ElGamal`, `ECMQV`, BC `*withPLAIN-ECDSA`, SM2, GOST) and BC lightweight API classes.
2. JOSE / JWT libraries (`RS256`, `ES256`, `EdDSA` algorithm constants in JJWT and Nimbus).
3. `HPKEParameterSpec.of(KEM_DHKEM_*, ...)` detection once codekoll builds on JDK 26.
4. Hybrid TLS named groups: no post-quantum or hybrid group is exposed by JDK 26 (verified); revisit guidance when one ships.
5. Plumb the workspace test-root flag to rules (R6) and fix the `CK-INSECURE-RANDOM` SPEC.md/implementation drift.
6. `CK-CRYPTO-WEAK` reports `RSA/ECB/...` as an ECB-mode error (read in `WeakCryptoRule.java:128`, not reproduced).
7. No test keeps `module-info.java` `provides` and `META-INF/services` in agreement (R7).
8. No test keeps hardcoded rule and pack counts in docs in agreement with the registry (R8).
9. SPEC.md section 7 says SARIF carries the pack as a rule tag; the SARIF reporter emits no tags for any rule (found by `PqcGuardrailCliTest` during implementation).

## Appendix A. JDK 26 asymmetric and post-quantum services (Windows, all default providers)

Generated from the R1 probe output, not transcribed. Columns: request type, classification,
canonical name, aliases and OIDs, providers.

| Type | Class | Name | Aliases / OIDs | Providers |
|---|---|---|---|---|
| Signature | VULNERABLE | `Ed25519` | 1.3.101.112, OID.1.3.101.112 | SunEC |
| Signature | VULNERABLE | `Ed448` | 1.3.101.113, OID.1.3.101.113 | SunEC |
| Signature | VULNERABLE | `EdDSA` |  | SunEC |
| Signature | PQ-SAFE | `HSS/LMS` | 1.2.840.113549.1.9.16.3.17, OID.1.2.840.113549.1.9.16.3.17 | SUN |
| Signature | VULNERABLE | `MD2withRSA` | 1.2.840.113549.1.1.2, OID.1.2.840.113549.1.1.2 | SunMSCAPI, SunRsaSign |
| Signature | VULNERABLE | `MD5andSHA1withRSA` |  | SunJSSE |
| Signature | VULNERABLE | `MD5withRSA` | 1.2.840.113549.1.1.4, OID.1.2.840.113549.1.1.4 | SunMSCAPI, SunRsaSign |
| Signature | PQ-SAFE | `ML-DSA` |  | SUN |
| Signature | PQ-SAFE | `ML-DSA-44` | 2.16.840.1.101.3.4.3.17, OID.2.16.840.1.101.3.4.3.17 | SUN |
| Signature | PQ-SAFE | `ML-DSA-65` | 2.16.840.1.101.3.4.3.18, OID.2.16.840.1.101.3.4.3.18 | SUN |
| Signature | PQ-SAFE | `ML-DSA-87` | 2.16.840.1.101.3.4.3.19, OID.2.16.840.1.101.3.4.3.19 | SUN |
| Signature | VULNERABLE | `NONEwithDSA` | RawDSA | SUN |
| Signature | VULNERABLE | `NONEwithDSAinP1363Format` |  | SUN |
| Signature | VULNERABLE | `NONEwithECDSA` |  | SunEC |
| Signature | VULNERABLE | `NONEwithECDSAinP1363Format` |  | SunEC |
| Signature | VULNERABLE | `NONEwithRSA` |  | SunJCE, SunMSCAPI |
| Signature | VULNERABLE | `RSASSA-PSS` | 1.2.840.113549.1.1.10, OID.1.2.840.113549.1.1.10, PSS | SunMSCAPI, SunRsaSign |
| Signature | VULNERABLE | `SHA1withDSA` | 1.2.840.10040.4.3, 1.3.14.3.2.13, 1.3.14.3.2.27, DSA, DSAWithSHA1, DSS, OID.1.2.840.10040.4.3, SHA-1/DSA, SHA/DSA, SHA1/DSA, SHAwithDSA | SUN |
| Signature | VULNERABLE | `SHA1withDSAinP1363Format` |  | SUN |
| Signature | VULNERABLE | `SHA1withECDSA` | 1.2.840.10045.4.1, OID.1.2.840.10045.4.1 | SunEC, SunMSCAPI |
| Signature | VULNERABLE | `SHA1withECDSAinP1363Format` |  | SunEC |
| Signature | VULNERABLE | `SHA1withRSA` | 1.2.840.113549.1.1.5, 1.3.14.3.2.29, OID.1.2.840.113549.1.1.5 | SunMSCAPI, SunRsaSign |
| Signature | VULNERABLE | `SHA224withDSA` | 2.16.840.1.101.3.4.3.1, OID.2.16.840.1.101.3.4.3.1 | SUN |
| Signature | VULNERABLE | `SHA224withDSAinP1363Format` |  | SUN |
| Signature | VULNERABLE | `SHA224withECDSA` | 1.2.840.10045.4.3.1, OID.1.2.840.10045.4.3.1 | SunEC, SunMSCAPI |
| Signature | VULNERABLE | `SHA224withECDSAinP1363Format` |  | SunEC |
| Signature | VULNERABLE | `SHA224withRSA` | 1.2.840.113549.1.1.14, OID.1.2.840.113549.1.1.14 | SunRsaSign |
| Signature | VULNERABLE | `SHA256withDSA` | 2.16.840.1.101.3.4.3.2, OID.2.16.840.1.101.3.4.3.2 | SUN |
| Signature | VULNERABLE | `SHA256withDSAinP1363Format` |  | SUN |
| Signature | VULNERABLE | `SHA256withECDSA` | 1.2.840.10045.4.3.2, OID.1.2.840.10045.4.3.2 | SunEC, SunMSCAPI |
| Signature | VULNERABLE | `SHA256withECDSAinP1363Format` |  | SunEC |
| Signature | VULNERABLE | `SHA256withRSA` | 1.2.840.113549.1.1.11, OID.1.2.840.113549.1.1.11 | SunMSCAPI, SunRsaSign |
| Signature | VULNERABLE | `SHA3-224withDSA` | 2.16.840.1.101.3.4.3.5, OID.2.16.840.1.101.3.4.3.5 | SUN |
| Signature | VULNERABLE | `SHA3-224withDSAinP1363Format` |  | SUN |
| Signature | VULNERABLE | `SHA3-224withECDSA` | 2.16.840.1.101.3.4.3.9, OID.2.16.840.1.101.3.4.3.9 | SunEC |
| Signature | VULNERABLE | `SHA3-224withECDSAinP1363Format` |  | SunEC |
| Signature | VULNERABLE | `SHA3-224withRSA` | 2.16.840.1.101.3.4.3.13, OID.2.16.840.1.101.3.4.3.13 | SunRsaSign |
| Signature | VULNERABLE | `SHA3-256withDSA` | 2.16.840.1.101.3.4.3.6, OID.2.16.840.1.101.3.4.3.6 | SUN |
| Signature | VULNERABLE | `SHA3-256withDSAinP1363Format` |  | SUN |
| Signature | VULNERABLE | `SHA3-256withECDSA` | 2.16.840.1.101.3.4.3.10, OID.2.16.840.1.101.3.4.3.10 | SunEC |
| Signature | VULNERABLE | `SHA3-256withECDSAinP1363Format` |  | SunEC |
| Signature | VULNERABLE | `SHA3-256withRSA` | 2.16.840.1.101.3.4.3.14, OID.2.16.840.1.101.3.4.3.14 | SunRsaSign |
| Signature | VULNERABLE | `SHA3-384withDSA` | 2.16.840.1.101.3.4.3.7, OID.2.16.840.1.101.3.4.3.7 | SUN |
| Signature | VULNERABLE | `SHA3-384withDSAinP1363Format` |  | SUN |
| Signature | VULNERABLE | `SHA3-384withECDSA` | 2.16.840.1.101.3.4.3.11, OID.2.16.840.1.101.3.4.3.11 | SunEC |
| Signature | VULNERABLE | `SHA3-384withECDSAinP1363Format` |  | SunEC |
| Signature | VULNERABLE | `SHA3-384withRSA` | 2.16.840.1.101.3.4.3.15, OID.2.16.840.1.101.3.4.3.15 | SunRsaSign |
| Signature | VULNERABLE | `SHA3-512withDSA` | 2.16.840.1.101.3.4.3.8, OID.2.16.840.1.101.3.4.3.8 | SUN |
| Signature | VULNERABLE | `SHA3-512withDSAinP1363Format` |  | SUN |
| Signature | VULNERABLE | `SHA3-512withECDSA` | 2.16.840.1.101.3.4.3.12, OID.2.16.840.1.101.3.4.3.12 | SunEC |
| Signature | VULNERABLE | `SHA3-512withECDSAinP1363Format` |  | SunEC |
| Signature | VULNERABLE | `SHA3-512withRSA` | 2.16.840.1.101.3.4.3.16, OID.2.16.840.1.101.3.4.3.16 | SunRsaSign |
| Signature | VULNERABLE | `SHA384withDSA` | 2.16.840.1.101.3.4.3.3, OID.2.16.840.1.101.3.4.3.3 | SUN |
| Signature | VULNERABLE | `SHA384withDSAinP1363Format` |  | SUN |
| Signature | VULNERABLE | `SHA384withECDSA` | 1.2.840.10045.4.3.3, OID.1.2.840.10045.4.3.3 | SunEC, SunMSCAPI |
| Signature | VULNERABLE | `SHA384withECDSAinP1363Format` |  | SunEC |
| Signature | VULNERABLE | `SHA384withRSA` | 1.2.840.113549.1.1.12, OID.1.2.840.113549.1.1.12 | SunMSCAPI, SunRsaSign |
| Signature | VULNERABLE | `SHA512/224withRSA` | 1.2.840.113549.1.1.15, OID.1.2.840.113549.1.1.15 | SunRsaSign |
| Signature | VULNERABLE | `SHA512/256withRSA` | 1.2.840.113549.1.1.16, OID.1.2.840.113549.1.1.16 | SunRsaSign |
| Signature | VULNERABLE | `SHA512withDSA` | 2.16.840.1.101.3.4.3.4, OID.2.16.840.1.101.3.4.3.4 | SUN |
| Signature | VULNERABLE | `SHA512withDSAinP1363Format` |  | SUN |
| Signature | VULNERABLE | `SHA512withECDSA` | 1.2.840.10045.4.3.4, OID.1.2.840.10045.4.3.4 | SunEC, SunMSCAPI |
| Signature | VULNERABLE | `SHA512withECDSAinP1363Format` |  | SunEC |
| Signature | VULNERABLE | `SHA512withRSA` | 1.2.840.113549.1.1.13, OID.1.2.840.113549.1.1.13 | SunMSCAPI, SunRsaSign |
| KeyPairGenerator | VULNERABLE | `DiffieHellman` | 1.2.840.113549.1.3.1, DH, OID.1.2.840.113549.1.3.1 | SunJCE |
| KeyPairGenerator | VULNERABLE | `DSA` | 1.2.840.10040.4.1, 1.3.14.3.2.12, OID.1.2.840.10040.4.1 | SUN |
| KeyPairGenerator | VULNERABLE | `EC` | 1.2.840.10045.2.1, EllipticCurve, OID.1.2.840.10045.2.1 | SunEC |
| KeyPairGenerator | VULNERABLE | `Ed25519` | 1.3.101.112, OID.1.3.101.112 | SunEC |
| KeyPairGenerator | VULNERABLE | `Ed448` | 1.3.101.113, OID.1.3.101.113 | SunEC |
| KeyPairGenerator | VULNERABLE | `EdDSA` |  | SunEC |
| KeyPairGenerator | PQ-SAFE | `ML-DSA` |  | SUN |
| KeyPairGenerator | PQ-SAFE | `ML-DSA-44` | 2.16.840.1.101.3.4.3.17, OID.2.16.840.1.101.3.4.3.17 | SUN |
| KeyPairGenerator | PQ-SAFE | `ML-DSA-65` | 2.16.840.1.101.3.4.3.18, OID.2.16.840.1.101.3.4.3.18 | SUN |
| KeyPairGenerator | PQ-SAFE | `ML-DSA-87` | 2.16.840.1.101.3.4.3.19, OID.2.16.840.1.101.3.4.3.19 | SUN |
| KeyPairGenerator | PQ-SAFE | `ML-KEM` |  | SunJCE |
| KeyPairGenerator | PQ-SAFE | `ML-KEM-1024` | 2.16.840.1.101.3.4.4.3, OID.2.16.840.1.101.3.4.4.3 | SunJCE |
| KeyPairGenerator | PQ-SAFE | `ML-KEM-512` | 2.16.840.1.101.3.4.4.1, OID.2.16.840.1.101.3.4.4.1 | SunJCE |
| KeyPairGenerator | PQ-SAFE | `ML-KEM-768` | 2.16.840.1.101.3.4.4.2, OID.2.16.840.1.101.3.4.4.2 | SunJCE |
| KeyPairGenerator | VULNERABLE | `RSA` | 1.2.840.113549.1.1, 1.2.840.113549.1.1.1, OID.1.2.840.113549.1.1 | SunMSCAPI, SunRsaSign |
| KeyPairGenerator | VULNERABLE | `RSASSA-PSS` | 1.2.840.113549.1.1.10, OID.1.2.840.113549.1.1.10, PSS | SunRsaSign |
| KeyPairGenerator | VULNERABLE | `X25519` | 1.3.101.110, OID.1.3.101.110 | SunEC |
| KeyPairGenerator | VULNERABLE | `X448` | 1.3.101.111, OID.1.3.101.111 | SunEC |
| KeyPairGenerator | VULNERABLE | `XDH` |  | SunEC |
| KeyAgreement | VULNERABLE | `DiffieHellman` | 1.2.840.113549.1.3.1, DH, OID.1.2.840.113549.1.3.1 | SunJCE |
| KeyAgreement | VULNERABLE | `ECDH` |  | SunEC |
| KeyAgreement | VULNERABLE | `X25519` | 1.3.101.110, OID.1.3.101.110 | SunEC |
| KeyAgreement | VULNERABLE | `X448` | 1.3.101.111, OID.1.3.101.111 | SunEC |
| KeyAgreement | VULNERABLE | `XDH` |  | SunEC |
| KeyFactory | VULNERABLE | `DiffieHellman` | 1.2.840.113549.1.3.1, DH, OID.1.2.840.113549.1.3.1 | SunJCE |
| KeyFactory | VULNERABLE | `DSA` | 1.2.840.10040.4.1, 1.3.14.3.2.12, OID.1.2.840.10040.4.1 | SUN |
| KeyFactory | VULNERABLE | `EC` | 1.2.840.10045.2.1, EllipticCurve, OID.1.2.840.10045.2.1 | SunEC |
| KeyFactory | VULNERABLE | `Ed25519` | 1.3.101.112, OID.1.3.101.112 | SunEC |
| KeyFactory | VULNERABLE | `Ed448` | 1.3.101.113, OID.1.3.101.113 | SunEC |
| KeyFactory | VULNERABLE | `EdDSA` |  | SunEC |
| KeyFactory | PQ-SAFE | `HSS/LMS` | 1.2.840.113549.1.9.16.3.17, OID.1.2.840.113549.1.9.16.3.17 | SUN |
| KeyFactory | PQ-SAFE | `ML-DSA` |  | SUN |
| KeyFactory | PQ-SAFE | `ML-DSA-44` | 2.16.840.1.101.3.4.3.17, OID.2.16.840.1.101.3.4.3.17 | SUN |
| KeyFactory | PQ-SAFE | `ML-DSA-65` | 2.16.840.1.101.3.4.3.18, OID.2.16.840.1.101.3.4.3.18 | SUN |
| KeyFactory | PQ-SAFE | `ML-DSA-87` | 2.16.840.1.101.3.4.3.19, OID.2.16.840.1.101.3.4.3.19 | SUN |
| KeyFactory | PQ-SAFE | `ML-KEM` |  | SunJCE |
| KeyFactory | PQ-SAFE | `ML-KEM-1024` | 2.16.840.1.101.3.4.4.3, OID.2.16.840.1.101.3.4.4.3 | SunJCE |
| KeyFactory | PQ-SAFE | `ML-KEM-512` | 2.16.840.1.101.3.4.4.1, OID.2.16.840.1.101.3.4.4.1 | SunJCE |
| KeyFactory | PQ-SAFE | `ML-KEM-768` | 2.16.840.1.101.3.4.4.2, OID.2.16.840.1.101.3.4.4.2 | SunJCE |
| KeyFactory | VULNERABLE | `RSA` | 1.2.840.113549.1.1, 1.2.840.113549.1.1.1, OID.1.2.840.113549.1.1 | SunRsaSign |
| KeyFactory | VULNERABLE | `RSASSA-PSS` | 1.2.840.113549.1.1.10, OID.1.2.840.113549.1.1.10, PSS | SunRsaSign |
| KeyFactory | VULNERABLE | `X25519` | 1.3.101.110, OID.1.3.101.110 | SunEC |
| KeyFactory | VULNERABLE | `X448` | 1.3.101.111, OID.1.3.101.111 | SunEC |
| KeyFactory | VULNERABLE | `XDH` |  | SunEC |
| KEM | VULNERABLE | `DHKEM` |  | SunJCE |
| KEM | PQ-SAFE | `ML-KEM` |  | SunJCE |
| KEM | PQ-SAFE | `ML-KEM-1024` | 2.16.840.1.101.3.4.4.3, OID.2.16.840.1.101.3.4.4.3 | SunJCE |
| KEM | PQ-SAFE | `ML-KEM-512` | 2.16.840.1.101.3.4.4.1, OID.2.16.840.1.101.3.4.4.1 | SunJCE |
| KEM | PQ-SAFE | `ML-KEM-768` | 2.16.840.1.101.3.4.4.2, OID.2.16.840.1.101.3.4.4.2 | SunJCE |
| Cipher | VULNERABLE | `HPKE` |  | SunJCE |
| Cipher | VULNERABLE | `RSA` |  | SunJCE, SunMSCAPI |
| Cipher | VULNERABLE | `RSA/ECB/PKCS1Padding` |  | SunMSCAPI |
| AlgorithmParameters | VULNERABLE | `DiffieHellman` | 1.2.840.113549.1.3.1, DH, OID.1.2.840.113549.1.3.1 | SunJCE |
| AlgorithmParameters | VULNERABLE | `DSA` | 1.2.840.10040.4.1, 1.3.14.3.2.12, OID.1.2.840.10040.4.1 | SUN |
| AlgorithmParameters | VULNERABLE | `EC` | 1.2.840.10045.2.1, EllipticCurve, OID.1.2.840.10045.2.1 | SunEC |
| AlgorithmParameters | VULNERABLE | `RSASSA-PSS` | 1.2.840.113549.1.1.10, OID.1.2.840.113549.1.1.10, PSS | SunRsaSign |
| AlgorithmParameterGenerator | VULNERABLE | `DiffieHellman` | 1.2.840.113549.1.3.1, DH, OID.1.2.840.113549.1.3.1 | SunJCE |
| AlgorithmParameterGenerator | VULNERABLE | `DSA` | 1.2.840.10040.4.1, 1.3.14.3.2.12, OID.1.2.840.10040.4.1 | SUN |

## Appendix B. JDK 26 TLS and XML DSig names (probe output)

- **Named groups** (all classical, all KEY-EXCHANGE when set): `ffdhe2048`, `ffdhe3072`, `ffdhe4096`, `ffdhe6144`, `ffdhe8192`, `secp256r1`, `secp384r1`, `secp521r1`, `x25519`, `x448`.
- **Signature schemes** (all SIGNATURE when set): `dsa_sha1`, `dsa_sha256`, `ecdsa_secp256r1_sha256`, `ecdsa_secp384r1_sha384`, `ecdsa_secp521r1_sha512`, `ecdsa_sha1`, `ed25519`, `ed448`, `rsa_pkcs1_sha1`, `rsa_pkcs1_sha256`, `rsa_pkcs1_sha384`, `rsa_pkcs1_sha512`, `rsa_pss_pss_sha256`, `rsa_pss_pss_sha384`, `rsa_pss_pss_sha512`, `rsa_pss_rsae_sha256`, `rsa_pss_rsae_sha384`, `rsa_pss_rsae_sha512`.
- **Cipher suites**: 31 supported. Not reported: `TLS_AES_128_GCM_SHA256`, `TLS_AES_256_GCM_SHA384`, `TLS_CHACHA20_POLY1305_SHA256` (TLS 1.3, key exchange set by named groups) and `TLS_EMPTY_RENEGOTIATION_INFO_SCSV`. Reported: the 27 `TLS_DHE_DSS_*`, `TLS_DHE_RSA_*`, `TLS_ECDHE_ECDSA_*`, `TLS_ECDHE_RSA_*` suites. Older JDKs' `TLS_RSA_WITH_*`, `TLS_ECDH_*` and `SSL_*` names are matched by the key-exchange token between `TLS_`/`SSL_` and `_WITH_`.
- **XML DSig `SignatureMethod` constants**: vulnerable are `DSA_SHA1`, `DSA_SHA256`, `ECDSA_SHA1`, `ECDSA_SHA224`, `ECDSA_SHA256`, `ECDSA_SHA384`, `ECDSA_SHA512`, `ECDSA_SHA3_224`, `ECDSA_SHA3_256`, `ECDSA_SHA3_384`, `ECDSA_SHA3_512`, `ED25519`, `ED448`, `RSA_PSS`, `RSA_SHA1`, `RSA_SHA224`, `RSA_SHA256`, `RSA_SHA384`, `RSA_SHA512`, and the 9 `SHA*_RSA_MGF1` constants; not asymmetric are the 5 `HMAC_SHA*` constants.