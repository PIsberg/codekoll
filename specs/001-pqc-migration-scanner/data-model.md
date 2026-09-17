# Data Model: PQC Migration Scanner & Static Guardrail

**Feature**: `001-pqc-migration-scanner` | **Date**: 2026-09-17 | **Research**: [research.md](research.md)

Codekoll stores nothing; these are the in-memory shapes inside `io.codekoll.rules.pqc` plus the
existing `Finding` they produce. No SPI type changes except one new `RulePack` constant.

## RequestType (enum)

The platform API a site calls. Drives which catalog section applies and which rule reports.

| Value | Matched call | Rule |
|---|---|---|
| `SIGNATURE` | `Signature.getInstance` | CK-PQC-SIGNATURE |
| `KEY_AGREEMENT` | `KeyAgreement.getInstance` | CK-PQC-KEY-EXCHANGE |
| `KEM` | `KEM.getInstance` | CK-PQC-KEY-EXCHANGE |
| `CIPHER` | `Cipher.getInstance` | CK-PQC-KEY-EXCHANGE |
| `KEY_PAIR_GENERATOR` | `KeyPairGenerator.getInstance` | CK-PQC-KEY-MATERIAL |
| `KEY_FACTORY` | `KeyFactory.getInstance` | CK-PQC-KEY-MATERIAL |
| `ALGORITHM_PARAMETERS` | `AlgorithmParameters.getInstance` | CK-PQC-KEY-MATERIAL |
| `ALGORITHM_PARAMETER_GENERATOR` | `AlgorithmParameterGenerator.getInstance` | CK-PQC-KEY-MATERIAL |
| `PARAMETER_SPEC` | parameter-spec constructors and `NamedParameterSpec` constants | CK-PQC-KEY-MATERIAL |
| `TLS_NAMED_GROUP` | `SSLParameters.setNamedGroups`, property `jdk.tls.namedGroups` | CK-PQC-KEY-EXCHANGE |
| `TLS_CIPHER_SUITE` | `setCipherSuites`, `setEnabledCipherSuites` | CK-PQC-KEY-EXCHANGE |
| `TLS_SIGNATURE_SCHEME` | `SSLParameters.setSignatureSchemes`, properties `jdk.tls.{client,server}.SignatureSchemes` | CK-PQC-SIGNATURE |
| `XML_SIGNATURE_METHOD` | `XMLSignatureFactory.newSignatureMethod` | CK-PQC-SIGNATURE |

The first eight are exactly the provider service types enumerated by the completeness test.

## Family (enum)

`RSA`, `RSASSA_PSS`, `DSA`, `EC`, `EDDSA`, `DH`, `XDH`, `DHKEM`, `HPKE`, plus `ML_KEM`, `ML_DSA`,
`HSS_LMS` for the safe side. Carries a display name used in messages ("ECDSA", "X25519") taken from
the matched catalog entry, not from the family, so the finding names what the code wrote.

## Role (enum)

| Value | Meaning | Replacement named in guidance |
|---|---|---|
| `KEY_ESTABLISHMENT` | confidentiality; recorded traffic exposed today | ML-KEM (FIPS 203), hybrid during transition |
| `SIGNATURE` | integrity and authenticity at verification time | ML-DSA (FIPS 204) |
| `KEY_MATERIAL` | key or parameters whose use is decided elsewhere | whichever of the above the family implies, or both |

## Classification (sealed)

- `Vulnerable(Family family, Role role, String displayName)`
- `PostQuantum(Family family)`
- `NotAsymmetric()`

## CatalogEntry

| Field | Type | Rule |
|---|---|---|
| `type` | `RequestType` | required |
| `canonicalName` | `String` | as registered by the JDK provider (Appendix A of research) |
| `aliases` | `Set<String>` | plain aliases, OIDs, `OID.`-prefixed OIDs |
| `classification` | `Classification` | required |

**Validation**
- Lookup key is `(type, name.toUpperCase(Locale.ROOT))`; canonical names and every alias map to the same entry.
- No key may map to two entries (unit test).
- For `CIPHER`, the looked-up name is the first `/`-separated segment, trimmed.
- `NotAsymmetric` for the eight provider types is decided by the family-prefix list in research R2, not by per-name entries.
- TLS suites classify by the key-exchange token between the `TLS_`/`SSL_` prefix and `_WITH_`: `RSA`, `DH_*`, `DHE_*`, `ECDH_*`, `ECDHE_*` are vulnerable; suites without `_WITH_` (TLS 1.3) and `TLS_EMPTY_RENEGOTIATION_INFO_SCSV` are not asymmetric.

## RequestSite (per match, transient)

| Field | Source |
|---|---|
| `tree` | the `MethodInvocationTree`, `NewClassTree` or `MemberSelectTree` reported |
| `type` | `RequestType` |
| `writtenNames` | constant string(s) as written; one for `getInstance`, one or more for arrays and property lists |
| `vulnerable` | the subset of `writtenNames` classified `Vulnerable` |
| `enclosingMethod` | for the hybrid and parameter-spec deduplication exemptions |

A site with an empty `vulnerable` set produces no finding. A site with several vulnerable names
produces one finding listing them (FR-007).

## PlatformSupport (per compilation unit, transient)

| Field | Derivation |
|---|---|
| `builtInMlKem` | `NamedParameterSpec` has field `ML_KEM_768` in the target release's symbol table |
| `builtInMlDsa` | `NamedParameterSpec` has field `ML_DSA_65` |

Selects between the two guidance variants in [contracts/rules.md](contracts/rules.md).

## Finding (existing, unchanged)

`Finding(RuleId rule, Severity severity, Path file, long line, long column, String message, String snippet)`.
Severity is always the rule's default. Message format is fixed in the rules contract.

## State transitions

None. Each compilation unit is scanned once per rule; the catalog is immutable and built once per
class load.