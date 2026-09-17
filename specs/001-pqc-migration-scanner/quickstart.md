# Quickstart: PQC Migration Scanner & Static Guardrail

How to confirm the feature works end to end once implemented. Every step names what it proves and
what failure looks like.

## Prerequisites

JDK 25 on `PATH`, from the repository root.

## 1. Build and run the pack's tests

```bash
mvn -pl codekoll-rules -am test
```

Proves: fixtures for all three rules (positive and negative) and the catalog completeness check pass
on this JDK. A missing catalog name fails here with the listing in
[contracts/catalog-completeness.md](contracts/catalog-completeness.md).

Check that the completeness test can fail: delete the `SHA3-512withECDSAinP1363Format` entry,
rerun, confirm red naming it, restore.

## 2. Inventory a sample tree

```java
// Sample.java
import java.security.*;
import javax.crypto.*;

class Sample {
  void classical() throws Exception {
    Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");   // CK-PQC-KEY-EXCHANGE
    Signature.getInstance("SHA256withECDSA");                      // CK-PQC-SIGNATURE
    KeyPairGenerator.getInstance("EC");                            // CK-PQC-KEY-MATERIAL
    KEM.getInstance("DHKEM");                                      // CK-PQC-KEY-EXCHANGE
  }

  // A separate method on purpose: DHKEM next to ML-KEM in one method is the
  // hybrid transition pattern and is exempt.
  void postQuantum() throws Exception {
    KEM.getInstance("ML-KEM");                                     // not reported
    Signature.getInstance("ML-DSA");                               // not reported
  }
}
```

```bash
mvn -pl codekoll-cli -am package -DskipTests
java -jar codekoll-cli/target/codekoll.jar --packs pqc path/to/sample
echo "exit=$?"
```

Expect: 4 findings (2 KEY-EXCHANGE WARNING, 1 SIGNATURE WARNING, 1 KEY-MATERIAL INFO) and `exit=0`
(FR-005). Messages say ML-KEM / ML-DSA are built in, because the sample targets release 25.

## 3. Guidance follows the target release

Analyze the same file as a project targeting Java 21 (e.g. a `pom.xml` with
`<maven.compiler.release>21</maven.compiler.release>`). Expect the same 4 findings with the "not
built into this project's Java release: use Bouncy Castle PQC or target Java 24+" wording.
`KEM.getInstance` exists from Java 21, so the sample still compiles.

## 4. Guardrail on new usage only (blocked until baseline support, Milestone 15)

Not runnable today: `--baseline` and `--write-baseline` are specified but not implemented. Once they
are:

```bash
java -jar codekoll-cli/target/codekoll.jar --write-baseline .codekoll-baseline.json path/to/sample
# add: Signature.getInstance("Ed25519");
java -jar codekoll-cli/target/codekoll.jar --baseline .codekoll-baseline.json --fail-on warning path/to/sample
echo "exit=$?"
```

Expect: exactly 1 finding (the Ed25519 line) and `exit=1` (SC-004).

Runnable today instead: append `// codekoll:off CK-PQC-SIGNATURE` to the `SHA256withECDSA` line and
confirm that finding disappears while the other 3 remain (User Story 2, scenario 4).

## 5. Explain

```bash
java -jar codekoll-cli/target/codekoll.jar --explain CK-PQC-KEY-EXCHANGE
```

Expect: vulnerability reason, urgency, not-a-drop-in caveat, hybrid pattern, both availability routes.

## 6. Repository gates

```bash
mvn -pl codekoll-examples -am test                       # examples fire / stay silent
mvn -pl codekoll-cli -am test -Dtest=ArchitectureTest    # metadata and layering
mvn -pl codekoll-load-test -am -Pquick verify -DskipTests # performance baseline
```

Report skipped or unavailable gates as such, never as passed.