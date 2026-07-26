# Codekoll

![build](https://img.shields.io/badge/build-passing-brightgreen)
![tests](https://img.shields.io/badge/tests-passing-brightgreen)
![examples](https://img.shields.io/badge/examples-108%2F108-brightgreen)
![quality](https://img.shields.io/badge/checkstyle%20%7C%20pmd%20%7C%20spotbugs-passing-brightgreen)
![archunit](https://img.shields.io/badge/archunit-passing-brightgreen)
![loadtest](https://img.shields.io/badge/loadtest-baseline%20v0-blue)
![rules](https://img.shields.io/badge/rules-108-blue)
![license](https://img.shields.io/badge/license-MIT-blue)

<!-- Badges point at the named CI jobs in .github/workflows/ci.yml (build, quality,
     coverage, archunit, examples, selfcheck, loadtest). Swap the shields.io placeholders
     for live workflow-status badges once the repo is on GitHub. -->

**Codekoll finds the Java bugs that compile perfectly and blow up in production.**

A standalone static analyzer for Java source, built in Java 25 on the JDK's own compiler. Point one jar at a source tree; get back a short list of findings where every single one is worth reading — each with what's wrong, what happens at runtime, and how to fix it.

```
src/main/java/com/acme/JobService.java:42:16  ERROR  CK-REF-EQUALITY
    if (status == "ACTIVE") {
               ^^
  `==` compares references, not contents. Use .equals().

✖ 3 errors, 5 warnings in 214 files (1.8s)
```

## Why codekoll, when SpotBugs and Error Prone exist?

Honest answer first: **most classic bug patterns overlap the incumbents.** SpotBugs has ~400 patterns from 20 years of hard lessons; Error Prone's type analysis is excellent; PMD, SonarQube, find-sec-bugs, and NullAway each own a slice. Codekoll's own build runs several of them as quality gates. If a rule catalog were the whole product, you wouldn't need this tool.

Codekoll differs in *how* it works — and in one area, in *what* it finds:

1. **Every finding is worth reading.** A curated catalog of 108 rules, each with an explicit false-positive budget: exemptions are part of every rule's contract and covered by tests, heuristic rules are demoted to INFO and never break your build, and any false positive found in the wild becomes a regression test before it's fixed. The opposite philosophy of "400 patterns your team disables wholesale."

2. **Zero build integration.** SpotBugs needs your compiled bytecode. Error Prone must be wired into your javac invocation. Codekoll is one jar pointed at a source tree — no build-file changes, works on code you just checked out, drops into any CI as a single step with SARIF output for GitHub code scanning.

3. **Rules that teach.** Every rule carries *what is wrong*, *what happens at runtime*, and *how to fix it* as first-class metadata — shown in findings, via `codekoll --explain CK-…`, and in a runnable examples project with a documented buggy/fixed pair for all 108 rules. Compare that to decoding `RV_RETURN_VALUE_IGNORED`.

4. **Modern Java, day one.** Codekoll parses with the JDK's own compiler (`JavacTask`), so new Java syntax works the day the JDK ships — no waiting for a third-party parser to catch up.

5. **Coverage where the incumbents are thin.** Two whole packs of it. The `modern` pack targets Java 21+ features that predate most incumbent catalogs: `default` branches that silently defeat sealed-switch exhaustiveness, records with array components (broken `equals`), pooled virtual threads, `Subtask.get()` before `join()`, `Instant.plus(MONTHS)` (always throws), DST-unsafe `Duration` arithmetic, reused streams, FFM use-after-close. The `frameworks` pack hunts **silently ignored code** — the sneakiest bug class there is, because nothing ever throws: `@Transactional` on a private method (no transaction, no error), `@Autowired` on a static field (stays null), a `@Test` that's silently never run, a JPA entity that's `final`, SLF4J placeholder mismatches, log calls that lose the stack trace. Plus JSpecify-aware nullness rules like overrides that weaken a supertype's nullness contract.

6. **Extensible without forking.** Rules ship in packs discovered via `ServiceLoader` across JPMS module boundaries. A team-specific rule pack is just a jar on `--rule-path`.

## What it finds

108 rules in ten packs — all bugs that **compile cleanly and fail (or silently misbehave) at runtime**:

| Pack | Focus | Rules | Flavor |
|---|---|---|---|
| `correctness` | Provably or almost-certainly wrong logic | 26 | two `next()` per one `hasNext()` guard, side effects inside `assert` (silently skipped in production), discarded `String.trim()`, `==` on Strings, `x.equals(null)`, the `YYYY` week-year date bug, format-string mismatches, infinite recursion, `getBytes()` without a charset, arrays as hash keys |
| `numeric` | Arithmetic and overflow traps | 8 | `long ms = days * 86_400_000` (overflowed before widening), comparator by subtraction, `Math.abs(hashCode())`, `1 << 32` |
| `concurrency` | Threading and synchronization | 11 | `Thread.run()` instead of `start()`, `synchronized` on a `Lock`, `++` on volatile, double-checked locking without `volatile`, collection modified while iterating, discarded `CompletableFuture`s |
| `resources` | Leaks, exception handling, cleanup | 9 | unclosed streams, empty catch blocks, rethrow that drops the cause, `throw` inside `finally` |
| `security` | Crypto, injection, TLS, ReDoS | 11 | MD5/SHA-1, SQL/command built by concatenation, trust-all TLS, XXE-default XML factories, catastrophic-backtracking regexes |
| `performance` | Accidentally-quadratic code | 6 | string concat in loops, regex recompiled per iteration, `keySet()`+`get()`, boxed accumulators |
| `api-misuse` | Stdlib contracts violated | 12 | `Arrays.asList(intArray)` (a list of size 1, not of ints), `Map.of("k", null)` and duplicate `Set.of`/`Map.of` keys (both always throw), `Boolean.getBoolean(value)` (reads a system property, always false), `Map<String,User>.get(12345)` (always null), mutating `List.of()`, `(String[]) list.toArray()`, `split(".")`, `List<Integer>.remove(1)` (index, not value!), `toMap` without a merge function |
| `nullness` | NPEs the compiler can't see | 8 | `int x = map.get(k)` (unboxing NPE), `x != null & x.length() > 0` (non-short-circuit), impossible null conditions, JSpecify contract violations |
| `modern` | Java 21+ platform misuse | 10 | sealed-switch `default`, record array components, pooled virtual threads, structured-concurrency ordering, `java.time` unit traps, stream reuse, FFM use-after-close |
| `frameworks` | Silently ignored code | 7 | `@Transactional` on a private method, proxy-bypassing self-invocation, `@Autowired static`, `final` JPA entities, tests that never run, SLF4J placeholder mismatches, stack traces lost in log calls |

The complete generated catalog — every rule's id, severity and one-line summary — is in [docs/RULES.md](docs/RULES.md) (produced by `codekoll --catalog`); each rule's detection algorithm and exemption list is in [SPEC.md](SPEC.md), and each has a runnable, documented example in [`codekoll-examples`](codekoll-examples/) showing the bug, the runtime failure, and the fix.

## How to run

Requires **JDK 25**.

```bash
# analyze a source tree
java -jar codekoll.jar src/main/java

# machine-readable output for CI / GitHub code scanning
java -jar codekoll.jar --format sarif --output codekoll.sarif src/main/java

# only some packs, stricter threshold
java -jar codekoll.jar --packs security,concurrency --fail-on warning src/main/java

# what does a rule mean?
java -jar codekoll.jar --explain CK-WEEK-YEAR-FORMAT
```

Exit codes: `0` clean, `1` findings at/above the `--fail-on` threshold, `2` usage/internal error. Configure per-project via `codekoll.toml` (disable rules or packs, adjust severities, exclude generated sources); suppress single findings with `@SuppressWarnings("codekoll:CK-…")`. Full CLI and config reference in [SPEC.md](SPEC.md) §3.4–3.5.

**The 30-second demo:** run codekoll on its own examples module and watch all 108 rules fire —

```bash
java -jar codekoll.jar codekoll-examples/src/main/java
```

## How it works

Codekoll drives the JDK compiler itself: sources are parsed and **attributed** by `javac` (`JavacTask.parse()` + `analyze()`), giving every rule fully resolved types, generics, and symbols — the same answers the compiler has. A single combined traversal walks each file once, dispatching AST nodes to the rules subscribed to them; findings stream to console, JSON, or SARIF reporters.

Because analysis is source-level and compiler-backed: analyzed code must compile (files with hard errors are reported as skipped), results are exact rather than decompiled guesses, and there is exactly one runtime dependency (the CLI parser). Architecture details — module graph, rule SPI, dispatcher design — in [ARCHITECTURE.md](ARCHITECTURE.md).

## Project status

In active development — see [PLAN.md](PLAN.md) for the milestone roadmap and [SPEC.md](SPEC.md) for the complete design. The quality bar codekoll holds itself to: Checkstyle, PMD, SpotBugs, Error Prone + NullAway (JSpecify), ArchUnit, and ≥90 % line coverage all fail the build, and codekoll runs on its own source in CI (`--fail-on error`).

Performance is held to the same standard: a load-test harness (`codekoll-load-test`) measures CPU and peak memory on every build against a committed baseline, fails CI on regression, and charts every version — the trend diagrams live in [`docs/perf/`](docs/perf/).

<!-- docs/perf/trend.png embedded here once baseline v0 exists (Milestone 9) -->

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
