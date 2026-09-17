# Contract: `pqc` rule pack

The user-facing interface of this feature is codekoll's existing CLI and output formats. Nothing new
is added to the CLI; this contract fixes what the new pack exposes through them, so tests can assert
it and users can depend on it.

## Pack

| Property | Value |
|---|---|
| Pack id (`--packs`, `rules.disable-packs`, `--catalog` heading) | `pqc` |
| Enabled by default | yes |
| Position in `--catalog` | after `frameworks` |

## Rules

| Id | Default severity | `description()` |
|---|---|---|
| `CK-PQC-KEY-EXCHANGE` | WARNING | Quantum-vulnerable key establishment (RSA encryption, (EC)DH, XDH, DHKEM, HPKE, classical TLS groups and suites) |
| `CK-PQC-SIGNATURE` | WARNING | Quantum-vulnerable digital signature (RSA, RSASSA-PSS, DSA, ECDSA, EdDSA; TLS signature schemes; XML signature methods) |
| `CK-PQC-KEY-MATERIAL` | INFO | Quantum-vulnerable key pair, key factory or algorithm parameters (RSA, EC, DSA, DH, XDH, EdDSA) |

All three: no ERROR default (FR-004); overridable per id in `[severity]`; suppressible on a line with
`// codekoll:off <id>` (element-level `@SuppressWarnings("codekoll:<id>")` is planned in SPEC.md
section 3.4 but not implemented); baselined like any other rule once baseline support
(Milestone 15) exists.

## Exit codes

Unchanged. At the default `--fail-on error`, a run whose only findings come from `pqc` exits `0`
(FR-005). At `--fail-on warning`, `CK-PQC-KEY-EXCHANGE` and `CK-PQC-SIGNATURE` findings exit `1`;
`CK-PQC-KEY-MATERIAL` never does unless its severity is raised.

## Finding message

One line, then codekoll's existing `fix()` tail. Placeholders in `<>`.

**KEY-EXCHANGE, built-in available**
```
<name> key establishment is quantum-vulnerable: traffic recorded today can be decrypted later. Replace with ML-KEM (FIPS 203), built into this project's Java platform (KEM.getInstance("ML-KEM")); run it hybrid with <name> until peers migrate.
```

**KEY-EXCHANGE, not built in** (uses the `<availability>` wording below, like every other message)
```
<name> key establishment is quantum-vulnerable: traffic recorded today can be decrypted later. Replace with ML-KEM (FIPS 203), not built into this project's Java release: use Bouncy Castle PQC or target Java 24+; run it hybrid with <name> until peers migrate.
```

**SIGNATURE** (same two variants, with):
```
<name> signatures are quantum-vulnerable: a future quantum computer can forge them. Replace with ML-DSA (FIPS 204), <availability>.
```

**KEY-MATERIAL**
```
<name> key material is quantum-vulnerable (<role or "used for signatures or key establishment">). Plan its replacement with <ML-KEM | ML-DSA | ML-KEM or ML-DSA>, <availability>.
```

For multi-name sites (TLS arrays, property lists) `<name>` is the comma-separated vulnerable subset in
source order, e.g. `x25519, secp256r1`.

`<availability>` is `built into this project's Java platform (<API call>)` or
`not built into this project's Java release: use Bouncy Castle PQC or target Java 24+`.

## `--explain <id>` content (FR-010)

`explanation()` must state, in this order: why the family is quantum-vulnerable (Shor's algorithm);
the urgency for the role; that the replacement is not a drop-in (for key establishment: KEM
encapsulation replaces encryption / agreement, `Cipher.getInstance("ML-KEM")` does not exist; for
signatures: larger keys and signatures, peers must support ML-DSA); and that the tool never rewrites
code. `fix()` must name the replacement, the hybrid transition pattern, and both availability
routes (built in from Java 24, Bouncy Castle PQC before).

Architecture test floor already enforced: non-blank, `explanation()` longer than 40 characters.

## Not reported (each has a negative fixture)

Post-quantum names (ML-KEM*, ML-DSA*, HSS/LMS, SLH-DSA*); symmetric, hash, MAC and PBE names; TLS 1.3
suites and the renegotiation SCSV; names not known at compile time; DH/ECDH/XDH/DHKEM in a method that
also requests any ML-KEM name; a parameter spec in a method that already reported a key-material
`getInstance`; any file under a test source directory (research R6).