# Contract: catalog completeness check (FR-016)

A test in `codekoll-rules` that turns "covers all of Java's vulnerable algorithms" into a build
failure when it stops being true.

## Inputs

The JDK running the test (CI: Temurin 25 on ubuntu-latest; local: any JDK 25+).

1. For each provider in `Security.getProviders()`, each `Provider.Service` whose type is one of
   `Signature`, `KeyPairGenerator`, `KeyAgreement`, `KeyFactory`, `KEM`, `Cipher`,
   `AlgorithmParameters`, `AlgorithmParameterGenerator`: its algorithm name.
2. For the same providers, each property key `Alg.Alias.<type>.<alias>` for those types: the alias.
3. `SSLContext.getDefault().getSupportedSSLParameters()`: cipher suites, named groups, and signature
   schemes (when non-null).
4. Every `public static final String` field of `javax.xml.crypto.dsig.SignatureMethod`.

## Pass condition

Every input name resolves in the catalog to `Vulnerable`, `PostQuantum` or `NotAsymmetric`.

## Failure output

One line per unclassified name, sorted, all reported in one run (not first-failure):

```
Unclassified by the pqc catalog on <java.vendor> <java.version>:
  KeyAgreement  ML-KEM-X25519        (provider SunEC)
  Signature     SLH-DSA-SHA2-128s    (provider SUN)
```

## Must also fail when

- a catalog entry that the JDK registers is deleted (verified once by hand during implementation);
- the signature-scheme list is null: the check then asserts the catalog's own scheme list is
  non-empty instead of passing on an empty input.

## Out of scope

Providers not installed by default (Bouncy Castle), which is follow-up issue 1 in research.