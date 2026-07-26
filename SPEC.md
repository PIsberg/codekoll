# Codekoll — Specification

A static analysis tool for Java source code, written in **Java 25**, that detects bugs which compile cleanly but fail or misbehave at runtime. Inspired by SpotBugs and JSpecify.

---

## 1. Goals & Non-Goals

### Goals
- Analyze Java source files (up to and including Java 25 language features) and report a curated set of high-confidence runtime-bug patterns.
- Run as a standalone CLI suitable for local use and CI pipelines.
- Produce human-readable console output and machine-readable output (SARIF 2.1.0 + JSON) for IDE/CI integration.
- Low false-positive rate: every rule is tuned to flag only patterns that are almost certainly bugs.
- Extensible, modular rule architecture: rules ship in **packs**, adding a rule requires implementing one interface, and third-party rule packs plug in via `ServiceLoader` without core changes.
- Every rule is self-documenting: its metadata carries *what is wrong*, *what happens at runtime*, and *how to fix it* — surfaced in findings, generated docs, and the examples module.

### Non-Goals (v1)
- Bytecode analysis (source-only; unlike SpotBugs which analyzes `.class` files).
- Whole-program interprocedural dataflow analysis (rules are method-local).
- Automatic fixing / rewriting (may suggest fixes in messages, but never edits code).
- IDE plugins (SARIF output makes IDE consumption possible without one).

### Prior art & differentiation

Honest positioning: **most classic bug patterns here overlap SpotBugs, Error Prone, PMD, SonarQube, find-sec-bugs, and NullAway** — that is inherent to codifying well-known hard lessons, and codekoll's own build runs several of those tools as quality gates. Codekoll differentiates on *how*, plus one area of *what*:

1. **Curation over coverage.** A deliberately small catalog where every rule has an explicit false-positive budget (§8) — "every finding is worth reading" instead of 400 patterns teams disable wholesale.
2. **Zero build integration.** SpotBugs needs compiled bytecode; Error Prone must be wired into javac. Codekoll is one jar pointed at a source tree.
3. **Self-documenting rules.** `explanation()`/`fix()` metadata, `--explain`, and a documented buggy/fixed example pair per rule — teaching material, not terse pattern codes.
4. **Modern-JDK-first.** Built on the JDK's own compiler API → day-one support for new Java syntax, where third-party parsers lag by months.
5. **Unique coverage where incumbents are thin:** the `modern` pack (§6.9) — records, sealed-type switches, virtual threads, structured concurrency, FFM, `java.time` traps — and the `frameworks` pack (§6.10) — **silently ignored code**, annotations that compile and run without error but quietly do nothing (`@Transactional` on a private method, a `@Test` that never runs). Plus JSpecify-aware nullness rules. Most incumbent catalogs predate the former and barely touch the latter.
6. **Extensible by design.** Rule packs are ServiceLoader modules; a team-specific pack is a jar on `--rule-path`, no fork required.

---

## 2. Technology Choices

| Decision | Choice | Rationale |
|---|---|---|
| Language / runtime | Java 25 | Requirement; records, sealed interfaces, and pattern matching for `switch` fit AST work extremely well. |
| Parser & type resolution | **JDK Compiler Tree API** (`com.sun.source.*`, `javax.lang.model.*`) via `JavacTask` | Ships with the JDK, so Java 25 syntax support is guaranteed day one (third-party parsers lag new syntax). Calling `JavacTask.analyze()` gives fully *attributed* trees — resolved types, generics, symbols — which most rules require. No external parsing dependency. |
| Modularity | **JPMS (`module-info.java`) on every production module** + multi-module Maven | See §3.1 — the SPI/ServiceLoader design is exactly what JPMS `provides/uses` was built for, and strong encapsulation gives compile-time teeth to the architecture rules ArchUnit checks at test time. |
| Build tool | Maven (multi-module) | Simple, ubiquitous in CI. |
| Test framework | JUnit 5 + fixture-based rule tests | See §9. |
| Distribution | Executable fat jar (`java -jar codekoll.jar`) + optional `jlink`/`jpackage` runtime image later (enabled by JPMS) | Zero-install friction for CI. |

**Why not JavaParser?** JavaParser's symbol solver is convenient, but its support for the newest language features historically trails JDK releases, and generic-type resolution (needed for several rules) is its weakest area. The javac API gives exact answers because it *is* the compiler.

**Key consequence:** the analyzer requires source that *compiles* (or at least parses + attributes). Files with hard compile errors are reported as skipped, not analyzed.

---

## 3. Architecture

### 3.1 Module structure (Maven modules ≙ JPMS modules)

```
codekoll-api        module io.codekoll.api       Rule SPI: Rule, RulePack, Finding, RuleId,
                                                 Severity, FindingCollector. Zero dependencies.
codekoll-engine     module io.codekoll.engine    Compilation driver (JavacTask), suppression,
                                                 rule registry. requires io.codekoll.api,
                                                 jdk.compiler; uses io.codekoll.api.Rule.
codekoll-rules      module io.codekoll.rules     All built-in rule packs, one package per pack
                                                 (…rules.correctness, …rules.numeric, …).
                                                 provides io.codekoll.api.Rule with <all rules>.
codekoll-report     module io.codekoll.report    Console / JSON / SARIF reporters.
                                                 requires io.codekoll.api only.
codekoll-cli        module io.codekoll.cli       picocli front-end, config loading, wiring.
                                                 The fat-jar / jlink entry point.
codekoll-examples   (unnamed module, no JPMS)    Intentionally buggy demo project + E2E tests.
codekoll-load-test  (unnamed module, no JPMS)    Perf harness: corpora, CPU/memory measurement,
                                                 baseline regression gate, per-version charts.
                                                 Internal only, never shipped.
```

Why JPMS is the right fit (and not ceremony):

- **Rule discovery is a textbook `ServiceLoader` use case.** `codekoll-rules` declares `provides io.codekoll.api.Rule with …`; the engine declares `uses io.codekoll.api.Rule`. A third-party rule pack is just another modular jar on the module path — discovered automatically, no registry edits, no reflection hacks.
- **The compiler dependency becomes explicit and contained.** Only `io.codekoll.engine` (and rule implementations via the API's re-exported `Trees`/`Types` handles) can see `jdk.compiler`. `codekoll-report` and `codekoll-cli` *cannot* accidentally grow a dependency on compiler internals — the module system refuses at compile time.
- **`com.sun.tools.javac.*` is doubly banned:** JPMS doesn't export it, and ArchUnit tests assert no such import exists. Public `com.sun.source.*` API only.
- **Encapsulation of rule internals:** `io.codekoll.rules` exports nothing (rules are reached only through the SPI), so helper classes inside packs are genuinely private.
- **Future `jlink` runtime image** for a small self-contained distribution comes for free.

`codekoll-examples` deliberately stays a plain (unnamed-module) project: it represents typical user code, and its intentional bugs shouldn't fight module-related tooling.

### 3.2 Runtime pipeline

```
┌──────────────┐   ┌──────────────┐   ┌────────────────────┐   ┌───────────────┐
│ cli           │──▶│ engine        │──▶│ rules (via SPI)    │──▶│ report        │
│ picocli, toml │   │ JavacTask     │   │ TreePathScanner    │   │ console/JSON/ │
│ config        │   │ parse+analyze │   │ per rule           │   │ SARIF         │
└──────────────┘   └──────────────┘   └────────────────────┘   └───────────────┘
                          │                    │
                          ▼                    ▼
                   Attributed ASTs        List<Finding>
```

### 3.3 Core abstractions (in `io.codekoll.api`)

```java
/** One detected problem instance. */
public record Finding(
    RuleId rule,
    Severity severity,        // ERROR, WARNING, INFO
    Path file,
    long line, long column,
    String message,           // human-oriented, includes the suggested fix
    String snippet            // offending source excerpt
) {}

/** Implemented once per rule; discovered via ServiceLoader. */
public interface Rule {
    RuleId id();              // e.g. "CK-EMPTY-CATCH"
    RulePack pack();          // CORRECTNESS, NUMERIC, CONCURRENCY, RESOURCES,
                              // SECURITY, PERFORMANCE, API_MISUSE, NULLNESS,
                              // MODERN, FRAMEWORKS
    Severity defaultSeverity();
    String description();     // one line: what the rule looks for
    String explanation();     // what is wrong + what happens at runtime
    String fix();             // how to fix it, one or two sentences
    /** Visit one attributed compilation unit; report via the collector. */
    void scan(CompilationUnitTree unit, Trees trees, Types types,
              Elements elements, FindingCollector out);
}
```

`description()`, `explanation()`, and `fix()` are **load-bearing metadata**, not decoration: the README rule catalog, the `codekoll-examples` per-pack READMEs, the SARIF rule descriptors, and the tail of every finding message are all generated from them — one source of truth, so docs cannot drift from behavior.

- Each rule extends a shared `TreePathScanner<Void, Void>` base class (`AbstractRule`, in `io.codekoll.rules`, internal) providing helpers: `resolvedType(tree)`, `isSubtypeOf(type, fqn)`, `isStringType(type)`, `enclosingLoop(path)`, `sourceSnippet(tree)`, `constantValue(tree)`, `sameSymbol(a, b)`.
- The **engine** builds one `JavacTask` per run (all files together, so cross-file symbols resolve), calls `parse()` then `analyze()`, and hands each `CompilationUnitTree` to every enabled rule.
- Rules never throw: a rule crash on one file is caught, logged, and analysis continues (`--strict` turns this into failure).

### 3.4 Configuration

`codekoll.toml` at project root (all optional):

```toml
[rules]
disable = ["CK-CRYPTO-WEAK"]      # individual rules
disable-packs = ["performance"]   # whole packs

[severity]
"CK-THREAD-RUN" = "error"

[suppress]
paths = ["**/generated/**", "**/target/**"]
```

- Inline suppression: `@SuppressWarnings("codekoll:CK-EMPTY-CATCH")` on the enclosing element, or trailing comment `// codekoll:off CK-EMPTY-CATCH`.
- CLI flags override config file.

### 3.5 CLI

```
codekoll [OPTIONS] <path>...
  --classpath <cp>        classpath for type resolution of dependencies
  --release <n>           --release passed to javac (default 25)
  --format console|json|sarif   (default console)
  --output <file>         write report to file instead of stdout
  --fail-on error|warning|never   exit-code threshold (default error)
  --rules <ids>           comma list; only run these rules
  --packs <names>         comma list; only run these packs
  --rule-path <jars>      extra module path entries scanned for third-party rule packs
  --explain <id>          print a rule's explanation, fix, and example, then exit
  --config <file>         explicit config path
```

Exit codes: `0` clean / below threshold, `1` findings at/above threshold, `2` usage or internal error.

---

## 4. Rule Packs — Overview

Rules are grouped into ten packs. The nine **founding rules** (from the original brief) are specified in full detail in §5; the **extended catalog** (§6) specifies every additional rule compactly. All ship in v1 (see PLAN.md for the build order); every rule — founding or extended — gets positive and negative fixtures, an example class documenting what is wrong and how to fix it, and a generated docs entry.

| Pack | Focus | Rules |
|---|---|---|
| `correctness` | Logic that is provably or almost-certainly wrong | 27 |
| `numeric` | Arithmetic and overflow traps | 8 |
| `concurrency` | Threading and synchronization | 13 |
| `resources` | Leaks, exception handling, cleanup | 9 |
| `security` | Weak crypto, injection, secrets, TLS, ReDoS | 11 |
| `performance` | Accidentally-quadratic and allocation churn | 6 |
| `api-misuse` | Standard-library contracts violated | 10 |
| `nullness` | NPEs the compiler can't see (JSpecify-aligned) | 8 |
| `modern` | Java 21+ platform misuse — records, sealed types, virtual threads, structured concurrency, FFM, `java.time` (differentiated coverage) | 10 |
| `frameworks` | **Silently ignored code** — annotations and logging contracts that compile, run without error, and quietly do nothing (differentiated coverage) | 7 |
| **Total** | | **109** |

---

## 5. Founding Rules (full detail)

Listed in implementation order (easiest → hardest). "Needs types" = requires attributed trees, not just syntax.

| # | ID | Pack | Name | Severity | Needs types |
|---|----|------|------|----------|-------------|
| 1 | `CK-EMPTY-CATCH` | resources | Silent exception swallower | WARNING | no |
| 2 | `CK-THREAD-RUN` | concurrency | `Thread.run()` instead of `start()` | ERROR | yes (trivial) |
| 3 | `CK-CRYPTO-WEAK` | security | Weak cryptographic algorithm | ERROR | yes (trivial) |
| 4 | `CK-IGNORED-RETURN` | correctness | Ignored return of pure/immutable method | WARNING | yes |
| 5 | `CK-REF-EQUALITY` | correctness | `==`/`!=` on String/boxed types | ERROR | yes |
| 6 | `CK-STR-CONCAT-LOOP` | performance | String concatenation in loop | WARNING | yes |
| 7 | `CK-RESOURCE-LEAK` | resources | AutoCloseable never closed | WARNING | yes |
| 8 | `CK-IMPOSSIBLE-COND` | nullness | Contradictory null condition (dead code) | WARNING | yes |
| 9 | `CK-GENERIC-MISMATCH` | api-misuse | Incompatible argument to `Map.get`/`Collection.remove` | ERROR | yes (generics) |

### 5.1 CK-EMPTY-CATCH — Silent exception swallower
- **Detect:** every `CatchTree` whose block contains zero statements (comments don't count — they aren't in the AST).
- **Exemptions (false-positive control):**
  - Caught exception variable named `ignored`/`ignore`/`expected` (established convention).
  - Caught type is `InterruptedException` **only if** the block re-interrupts — otherwise still flagged (an empty `InterruptedException` catch is its own classic bug).
- **Message:** "Empty catch block swallows `PaymentException`. Log it, rethrow it, or rename the variable to `ignored` if intentional."

### 5.2 CK-THREAD-RUN — Thread.run() called directly
- **Detect:** `MethodInvocationTree` where the method symbol is `run()` with no args and the receiver's resolved type is `java.lang.Thread` or a subtype **and** the call is not `super.run()` inside a `Thread` subclass's own `run()` override.
- **Message:** "`Thread.run()` executes on the *current* thread. Did you mean `start()`?"

### 5.3 CK-CRYPTO-WEAK — Weak crypto algorithm
- **Detect:** invocations of `MessageDigest.getInstance`, `Cipher.getInstance`, `Mac.getInstance`, `KeyGenerator.getInstance`, `SecretKeyFactory.getInstance` where argument 0 is a **string literal** (or a constant-folded `static final String`, via the attributed tree's constant value).
- **Blocklist (case-insensitive, matched on the algorithm segment before any `/` transformation suffix):** `MD2`, `MD5`, `SHA-1`, `SHA1`, `DES`, `DESede` (flag as INFO), `RC2`, `RC4`, `ARCFOUR`, `Blowfish`. Also flag `Cipher` transformations using `ECB` mode or `NoPadding` with block ciphers (INFO level).
- **Non-literal argument:** not flagged (v1) — no interprocedural constant propagation.
- **Message:** "MD5 is cryptographically broken (collision attacks). Use SHA-256 or stronger."

### 5.4 CK-IGNORED-RETURN — Ignored return value
- **Detect:** `ExpressionStatementTree` whose expression is a `MethodInvocationTree`, where the invoked method:
  - returns non-`void`, **and**
  - the receiver type is on the **known-pure list**: `String`, `BigDecimal`, `BigInteger`, all of `java.time.*`, `Optional`, `Stream` intermediate ops, `Path`, plus any method annotated `@CheckReturnValue` (JSR-305 / Error Prone / JSpecify-adjacent annotations, matched by simple name so any provider works).
- **Exemptions:** methods on the pure types legitimately called for effect or by convention: `Map.put`, `List.remove`, `StringBuilder.append` (fluent but mutating), `Optional.orElseThrow` (side effect = throw). The pure list is method-level, not just type-level: e.g. `String.intern()` still flagged.
- **Message:** "`String.trim()` returns a new string; the result is discarded. Assign it: `name = name.trim();`"

### 5.5 CK-REF-EQUALITY — == on String / boxed types
- **Detect:** `BinaryTree` of kind `EQUAL_TO`/`NOT_EQUAL_TO` where either operand's resolved type is `java.lang.String`, or a boxed type (`Integer`, `Long`, `Double`, `Float`, `Short`, `Byte`, `Character`, `Boolean`) **when the other operand is not a primitive** (comparison with a primitive unboxes and is fine).
- **Exemptions:**
  - Either operand is the `null` literal (`x == null` is correct and idiomatic).
  - Comparison inside an `equals()` implementation where one operand is `this` (the standard identity fast-path `if (this == obj)`).
  - Enum types are **not** flagged (`==` is correct for enums).
- **Boxed-type note:** `Integer == Integer` in the cache range works accidentally; still flagged as ERROR because it breaks outside −128..127.
- **Message:** "`==` compares references, not contents. Use `.equals()` (or `Objects.equals()` if either side may be null)."

### 5.6 CK-STR-CONCAT-LOOP — Concatenation in loop
- **Detect:** inside any `ForLoopTree` / `EnhancedForLoopTree` / `WhileLoopTree` / `DoWhileLoopTree` body:
  - `CompoundAssignmentTree` of kind `PLUS_ASSIGNMENT` where the variable's type is `String`, or
  - `AssignmentTree` of form `s = s + …` where `s` is a `String` variable, or
  - `s = s.concat(…)`.
  - The assigned variable must be declared **outside** the loop (a fresh `String` built and consumed within one iteration is O(1) per iteration and fine).
- **Exemption:** nothing flagged inside lambdas passed to unknown methods (loop context doesn't reliably transfer).
- **Message:** "String concatenation in a loop is O(n²). Build with `StringBuilder` and call `toString()` after the loop."

### 5.7 CK-RESOURCE-LEAK — AutoCloseable never closed
- **Detect:** a `NewClassTree` (or factory call known to open a resource: `Files.newInputStream/newOutputStream/newBufferedReader/newBufferedWriter/lines`, `Socket` accept, JDBC `getConnection/createStatement/prepareStatement/executeQuery`) whose resolved type implements `java.lang.AutoCloseable`, where the created value:
  - is **not** the resource of a try-with-resources (`TryTree.getResources()`), and
  - is **not** returned from the method, **not** passed as an argument to another call, **not** assigned to a field (ownership transfer — caller's responsibility), and
  - if assigned to a local variable: no `close()` call on that variable exists in a `finally` block of an enclosing `try` (v1 heuristic: any reachable `close()` on the variable within the method suppresses the finding, with a weaker INFO note if the `close()` is not in a `finally`).
- **Type allowlist (never leak-flagged):** `ByteArrayInputStream/OutputStream`, `StringReader/Writer`, `CharArrayReader/Writer` — closing these is a no-op; flagging them is pure noise.
- **Wrapper handling:** `new BufferedReader(new FileReader(f))` — the inner `new` is *consumed as an argument*, so only the outer wrapper is tracked (standard decorator/ownership-transfer semantics).
- **Message:** "`FileInputStream` is never closed on all paths. Use try-with-resources: `try (var stream = new FileInputStream(path)) { … }`"

### 5.8 CK-IMPOSSIBLE-COND — Contradictory null conditions
- **Detect (v1 scope — null-contradictions only, not full SAT):** within a single boolean expression, collect null-facts per operand of `&&` chains:
  - `x == null` establishes NULL(x); `x != null` establishes NONNULL(x).
  - Any dereference of `x` (`x.length()`, `x.field`, `x[i]`) *requires* NONNULL(x).
  - Flag when a `&&` chain establishes NULL(x) and a later conjunct dereferences `x` or asserts NONNULL(x) — the right side either always throws NPE or is always false → dead branch.
  - Symmetric case for `||`: `x != null || x.length() > 5` (right side only evaluated when x IS null → guaranteed NPE) — flagged as ERROR, distinct message.
  - Also: identical conjuncts with contradictory constant comparisons (`x == null && x != null`, `a > 5 && a < 3` for int constants).
- **Variables must be effectively unmodified between conjuncts** (method calls on/with `x` between the facts invalidate them, conservatively).
- **Message:** "Condition is impossible: `id == null` and `id.length() > 5` cannot both hold — this block is dead code (and would NPE if reached)."

### 5.9 CK-GENERIC-MISMATCH — Wrong-type argument to weakly-typed collection methods
- **Detect:** invocations of `java.util.Map#get/remove/containsKey/containsValue`, `java.util.Collection#remove/contains`, `java.util.List#indexOf/lastIndexOf` (all declared with `Object` params for backward compatibility).
  1. Resolve the receiver's *declared* type as an instance of the interface (e.g. `Map<String, User>`) using `Types.asMemberOf` / supertype walking to get the actual type argument for `K` (or `E`).
  2. Resolve the argument's static type.
  3. Flag if argument type and key/element type are **provably unrelated**: no casting conversion exists in either direction (`Types.isAssignable` both ways fails, and neither is a supertype of the other). `Object` arguments, unbounded wildcards, and raw receivers are never flagged.
- **Boxing:** compare boxed forms (`map.get(12345)` against `Map<String,…>` → `Integer` vs `String` → flagged; against `Map<Long,…>` → `Integer` vs `Long` → **also flagged**; this is the classic `int` literal vs `Long` key bug and gets a dedicated message).
- **Message:** "`Map<String, User>.get()` called with an `Integer`. This compiles but always returns null. Did you mean to pass a String?"

---

## 6. Extended Catalog

Each entry: what fires, key exemptions in *(italics)*. All are method-local and implementable with attributed ASTs — no interprocedural analysis. Severity E=ERROR, W=WARNING, I=INFO. INFO rules never affect exit codes at the default `--fail-on error`.

### 6.1 Pack `correctness`

| ID | Sev | Detection |
|---|---|---|
| `CK-SELF-ASSIGN` | E | `AssignmentTree` where LHS and RHS resolve to the **same symbol**: `x = x;`, `this.f = this.f;`, `f = this.f`. *(Not `this.x = x` param-to-field — different symbols.)* Almost always a typo for a shadowed parameter. |
| `CK-SELF-COMPARE` | E | `x == x`, `x != x`, `x.equals(x)`, `x.compareTo(x)` with structurally identical, side-effect-free operands (same symbol chain, no method calls). Constant result; usually a copy-paste bug. *(Exempt `x != x` on float/double — legitimate NaN idiom, but suggest `isNaN()` as INFO.)* |
| `CK-EQUALS-INCOMPATIBLE` | E | `a.equals(b)` where the resolved types of `a` and `b` are provably unrelated (same both-ways `isAssignable` logic as CK-GENERIC-MISMATCH) → always false at runtime. *(Raw/`Object`-typed operands never flagged.)* |
| `CK-EQUALS-HASHCODE` | W | A class overrides `equals(Object)` but not `hashCode()`, or vice versa. Breaks every hash-based collection. *(Exempt if the missing one is inherited from a superclass other than `Object`.)* |
| `CK-EQUALS-OVERLOAD` | W | A class declares `equals(SomeType)` (single non-`Object` parameter) **without** also overriding `equals(Object)` — it's an overload, not an override; collections and `Objects.equals` silently call the inherited identity version. |
| `CK-EQUALS-NULL-ARG` | E | `x.equals(null)` with a `null` literal argument — the `equals` contract guarantees `false` (and a broken implementation NPEs). The author almost always meant `x == null`. |
| `CK-COLLECTION-SELF-ADD` | E | `c.add(c)`, `c.addAll(c)`, `m.put(k, m)` where receiver and argument are the **same symbol** — self-containing collections make `hashCode()`/`toString()` recurse to `StackOverflowError`. |
| `CK-INFINITE-RECURSION` | E | A method or constructor whose body **unconditionally** invokes itself before any branch: first statement is `return this.m(args)` / `m(args)` / `this(args)` with the same resolved symbol. Guaranteed `StackOverflowError`. *(Any conditional before the self-call exempts — v1 does not prove termination, only the unconditional case.)* |
| `CK-SB-CHAR-CTOR` | E | `new StringBuilder('a')` / `new StringBuffer('x')` — the char widens to `int` and becomes the *capacity*; nothing is appended. Suggest `new StringBuilder("a")` or `.append('a')`. |
| `CK-WEEK-YEAR-FORMAT` | E | Date-format pattern literal (argument to `DateTimeFormatter.ofPattern`, `new SimpleDateFormat`) containing `YYYY` (week-based year) together with `MM`/`dd` and **no** `ww` — dates around New Year silently shift a year (the classic end-of-December production bug). Suggest `yyyy`. Also flag `hh` (1–12 clock) with no `a` AM/PM marker as INFO. |
| `CK-ARRAY-OBJECT-METHODS` | E | `equals`, `hashCode`, or `toString` invoked on an operand of array type — compares/prints identity, never contents. Suggest `Arrays.equals/hashCode/toString` (or `deepEquals` for nested arrays). |
| `CK-TOSTRING-ARRAY` | W | Array-typed expression used where implicit `toString()` happens: `+` with a String operand, argument to `println`/logger/`String.valueOf(Object)`, or `%s` argument of `String.format`. Prints `[Ljava.lang.String;@1a2b3c`. |
| `CK-NAN-COMPARE` | E | `==` or `!=` where one operand is `Double.NaN`/`Float.NaN` — always false/true by IEEE 754. Suggest `Double.isNaN(x)`. |
| `CK-FORMAT-MISMATCH` | E | `String.format`, `formatted`, `printf`, `Formatter.format`, and known logger format methods where the format string is a compile-time constant: parse the specifiers, flag (a) arg-count mismatch, (b) type-incompatible specifier (`%d` given a String), (c) `%s` given an array (routes to CK-TOSTRING-ARRAY message). *(Non-constant format strings skipped.)* |
| `CK-ASSIGN-IN-COND` | W | Assignment (`=`) as the condition of `if`/`while`/`do`/ternary: `if (done = true)`. *(Exempt the idiomatic read-loop `while ((line = reader.readLine()) != null)` — assignment wrapped in a comparison is fine; only a **bare** assignment of boolean type fires.)* |
| `CK-BIGDECIMAL-DOUBLE` | W | `new BigDecimal(double)` with a floating-point literal or double-typed expression — inherits binary imprecision (`new BigDecimal(0.1)` ≠ 0.1). Suggest `BigDecimal.valueOf` or the String constructor. |
| `CK-BIGDECIMAL-EQUALS` | I | `equals`/`hashCode` on `BigDecimal` operands — scale-sensitive (`1.0` ≠ `1.00`); most call sites mean `compareTo(...) == 0`. |
| `CK-EXCEPTION-NOT-THROWN` | E | `ExpressionStatementTree` whose expression is `new X(...)` where `X` is a `Throwable` subtype — the exception is constructed and discarded; the `throw` keyword was forgotten. |
| `CK-OPTIONAL-NULL` | E | `return null;` inside a method whose declared return type is `Optional<…>` (or `OptionalInt/Long/Double`). Defeats the type's entire purpose. Suggest `Optional.empty()`. |
| `CK-URL-EQUALS` | W | `equals`/`hashCode` on `java.net.URL` operands, or `URL` used as a key type in `Map`/`Set` construction — performs blocking DNS resolution per comparison. Suggest `URI`. |
| `CK-SWITCH-FALLTHROUGH` | I | Statement-`switch` case with executable statements that falls into the next case without `break`/`yield`/`return`/`throw` and without a `// fall through` comment. *(Empty grouped cases exempt; arrow-form `switch` can't fall through.)* |
| `CK-DEFAULT-CHARSET` | W | `String.getBytes()`, `new String(byte[])`, `new FileReader/FileWriter(...)`, `new InputStreamReader/OutputStreamWriter(stream)` **without an explicit charset** — behavior depends on the platform default; data written on one machine reads as mojibake on another. Pass `StandardCharsets.UTF_8`. |
| `CK-ARRAY-AS-KEY` | E | An array type used as a `Map` key or `Set` element type argument (`Map<int[], V>`, `Set<byte[]>`), or an array-typed argument to `contains`/`get`/`remove` on a hash collection — arrays use identity `hashCode`/`equals`, so lookups **never match** a different array with equal contents. Wrap in `List`/record, or use a `TreeMap` with `Arrays::compare`. |
| `CK-ASSERT-SIDE-EFFECT` | W | An `assert` whose condition or detail message changes state: an assignment, a compound assignment, an increment/decrement, or an invocation of a known mutator (`add`/`put`/`remove`/`next`/`append`/`incrementAndGet`/…) on a receiver resolving to `Collection`, `Map`, `Iterator`, `StringBuilder`/`StringBuffer` or an `Atomic*`. Assertions are disabled without `-ea`, so the mutation happens under test and silently never happens in production. *(Unqualified calls with no visible receiver, and mutator-named methods on any other receiver type, are never flagged — the name alone is not evidence.)* |
| `CK-WALLCLOCK-ELAPSED` | I | Elapsed time measured by subtracting two `System.currentTimeMillis()` readings — wall-clock time jumps (NTP corrections, DST, leap smearing), producing negative or wildly wrong durations. Use `System.nanoTime()` for intervals. |

*(Founding rules CK-IGNORED-RETURN and CK-REF-EQUALITY also belong to this pack — §5.4, §5.5.)*

### 6.2 Pack `numeric`

| ID | Sev | Detection |
|---|---|---|
| `CK-INT-OVERFLOW-WIDEN` | W | `int × int` arithmetic (`*`, `+` chains of multiplications) whose result flows into a `long` context (assignment, param, return) — the multiply already overflowed in 32 bits: `long ms = days * 86_400_000;`. Fires when at least one operand chain makes overflow plausible (any literal ≥ 1000 or non-constant operand). Suggest making one operand `long` (`86_400_000L`). |
| `CK-COMPARE-SUBTRACT` | W | `return a - b;` (or lambda `(a, b) -> a - b`) as the body of `compareTo`/`compare` where `a`,`b` are `int`/`long` — overflows for large-magnitude values, breaking sort contracts. Suggest `Integer.compare(a, b)`. |
| `CK-ABS-OVERFLOW` | W | `Math.abs(...)` applied to `hashCode()`, `Random.nextInt()`, or any `int` expression immediately used with `%` for indexing — `Math.abs(Integer.MIN_VALUE)` is negative. Suggest `Math.floorMod(x, n)`. |
| `CK-SHIFT-OOB` | E | Shift of an `int` by a constant ≥ 32 (or `long` by ≥ 64) — shift distance is taken mod 32/64, so `1 << 32 == 1`, never 0. |
| `CK-INT-DIV-FLOAT` | W | `int / int` division whose result immediately flows into a `double`/`float` context: `double ratio = hits / total;` — truncation already happened. Suggest casting an operand first. |
| `CK-FLOAT-EQUALITY` | I | `==`/`!=` between two floating-point expressions, neither a compile-time constant `0.0`/`NaN`/infinity — usually wants an epsilon comparison. *(Comparisons with `0.0` literal exempt — common and often intentional.)* |
| `CK-DIV-ZERO` | E | Division or modulo whose divisor is the integer literal `0`/`0L` (in a non-constant-expression position, which is why it compiled) — guaranteed `ArithmeticException` on every execution. |
| `CK-OCTAL-LITERAL` | W | Multi-digit `int`/`long` literal with a leading `0` (not `0x`/`0b`), all digits < 8 — accidental octal: `int timeout = 0100;` is 64, not 100. *(Exempt permission-triple-looking literals `0644`/`0755`/`0777` passed to a parameter named `mode`/`permissions`.)* |

### 6.3 Pack `concurrency`

| ID | Sev | Detection |
|---|---|---|
| `CK-THREAD-RUN` | E | Founding rule — §5.2. |
| `CK-SYNC-ON-VALUE` | E | `synchronized (expr)` where `expr`'s type is `String`, a boxed primitive, or the result of `Integer.valueOf`-style caching — these are interned/shared, so unrelated code can deadlock with you or your lock is not exclusive. |
| `CK-MONITOR-ON-LOCK` | E | `synchronized (expr)` where `expr`'s type implements `java.util.concurrent.locks.Lock` — the monitor and the `Lock` are independent mechanisms; this provides **zero** mutual exclusion against threads using `lock()`/`unlock()`. Suggest `lock.lock()` in a try/finally. |
| `CK-DCL-NO-VOLATILE` | W | Double-checked locking shape: `if (f == null) { synchronized (…) { if (f == null) { f = …; } } }` where field `f` is **not** `volatile`. Broken publication under the JMM. |
| `CK-VOLATILE-COMPOUND` | W | `++`, `--`, or compound assignment (`+=`, …) targeting a `volatile` field — read-modify-write is not atomic and `volatile` does not make it so; concurrent increments are lost. Suggest `AtomicInteger`/`AtomicLong` or a lock. |
| `CK-WAIT-NO-LOOP` | W | `Object.wait()`/`Condition.await()` whose enclosing statement chain contains no loop before the enclosing method — spurious wakeups make un-looped waits incorrect. |
| `CK-STATIC-DATEFORMAT` | E | `static` field (non-`ThreadLocal`) whose type is `SimpleDateFormat`, `DateFormat`, `Calendar`, or `NumberFormat` — documented non-thread-safe; shared static instances corrupt state under concurrency. Suggest `DateTimeFormatter` (immutable) or `ThreadLocal`. |
| `CK-LOCK-NO-FINALLY` | W | `Lock.lock()`/`lockInterruptibly()` call not immediately followed by a `try` whose `finally` unlocks the same lock variable within the method. |
| `CK-SLEEP-IN-SYNC` | I | `Thread.sleep(...)` inside a `synchronized` block or method — holds the monitor for the whole sleep, stalling every other thread that needs it. |
| `CK-CTOR-THREAD-START` | W | `Thread.start()` (or `ExecutorService.submit` capturing `this`) invoked inside a constructor — publishes `this` before construction completes. |
| `CK-CONCURRENT-MOD` | E | Inside an enhanced-`for` iterating collection `c`: a call to `c.add/remove/clear/put` on the **same symbol** `c` — guaranteed `ConcurrentModificationException` (or silent corruption). Suggest `Iterator.remove` or `removeIf`. |
| `CK-PARALLEL-MUTATION` | E | A lambda passed to `forEach`/`map`/`filter` on a stream made parallel in the same expression chain (`.parallelStream()`, `.parallel()`) that mutates a **captured non-concurrent collection** (`ArrayList.add`, `HashMap.put`, `StringBuilder.append`) — data race: silent corruption, lost elements, or `ArrayIndexOutOfBoundsException` under load. Use `collect(...)`/`Collectors.toConcurrentMap`, or drop `parallel()`. |
| `CK-FUTURE-DISCARDED` | W | An `ExpressionStatementTree` whose value is a `CompletableFuture`/`Future` returned by a method call — the future (and **any exception inside it**) is silently dropped; async failures vanish without a trace. Keep the future and handle it (`join`, `exceptionally`, collect for `allOf`). *(`void`-lambda `thenAccept`-style tails exempt — the chain itself was the handling.)* |

### 6.4 Pack `resources`

| ID | Sev | Detection |
|---|---|---|
| `CK-EMPTY-CATCH` | W | Founding rule — §5.1. |
| `CK-RESOURCE-LEAK` | W | Founding rule — §5.7. |
| `CK-THROW-IN-FINALLY` | W | `throw` or `return` statement inside a `finally` block — silently discards any in-flight exception from the `try` body. |
| `CK-LOST-CAUSE` | W | A `catch` block that throws a **new** exception whose constructor arguments do not include the caught variable, and the caught variable is otherwise unused in the block — the original stack trace is lost forever. Suggest passing it as the cause. *(Any other reference to the caught variable — logging, message extraction — exempts.)* |
| `CK-CATCH-NPE` | W | A `catch` clause explicitly listing `NullPointerException` — using NPE for control flow masks real bugs and catches unrelated NPEs from the whole `try` body. Fix the null source instead. *(Exempt when the catch rethrows wrapped — bridging third-party code.)* |
| `CK-CATCH-BROAD` | I | `catch (Throwable t)` or `catch (Error e)` — swallows `OutOfMemoryError`/`StackOverflowError` and linkage errors. *(Exempt when the block rethrows `t` or the enclosing class name matches `*Runner/*Executor/*Loop` — framework top-levels legitimately do this.)* |
| `CK-PRINT-STACKTRACE` | I | `e.printStackTrace()` on a caught exception — bypasses logging, lost in production. *(Exempt in classes with a `main` method and in test sources.)* |
| `CK-SYSTEM-EXIT` | I | `System.exit(...)` / `Runtime.halt(...)` outside the class containing `main` (or classes named `*Main`/`*Launcher`/`*Cli`) — library code killing the whole JVM takes the host application down with it. |
| `CK-FINALIZE` | W | Overriding `Object.finalize()` — deprecated for removal, non-deterministic, resurrection hazards. Suggest `Cleaner` or `AutoCloseable`. |

### 6.5 Pack `security`

| ID | Sev | Detection |
|---|---|---|
| `CK-CRYPTO-WEAK` | E | Founding rule — §5.3. |
| `CK-WEAK-TLS` | E | `SSLContext.getInstance(...)` with literal `"SSL"`, `"SSLv2"`, `"SSLv3"`, `"TLSv1"`, or `"TLSv1.1"` — protocols with known breaks (POODLE, BEAST). Suggest `"TLSv1.3"` (or `"TLSv1.2"` minimum). *(Bare `"TLS"` flagged as INFO — it negotiates, but pinning is better.)* |
| `CK-HARDCODED-SECRET` | W | Non-empty string literal assigned to a variable/field/constant whose name matches `(?i)(password|passwd|pwd|secret|api[_-]?key|token|credential)` — excluding names that also match `(?i)(prompt|label|field|param|name|key$)` false-friend patterns and values that are clearly placeholders (`""`, `"${…}"`/`"%s"` template forms exempt; `"changeme"` is still flagged). |
| `CK-SQL-CONCAT` | E | Argument to `Statement.execute/executeQuery/executeUpdate/addBatch` or `Connection.prepareStatement/prepareCall` is a string built with `+` (or `String.format`/`StringBuilder` chain) mixing literals with **non-constant** expressions — SQL injection shape. *(Pure-literal concatenation exempt.)* Suggest bind parameters. |
| `CK-EXEC-CONCAT` | E | Argument to `Runtime.exec(...)` or `new ProcessBuilder(...)` built by concatenating literals with non-constant expressions — command-injection shape (same detection machinery as CK-SQL-CONCAT). Suggest the list form with each argument separate. |
| `CK-XXE-FACTORY` | W | `DocumentBuilderFactory/SAXParserFactory/XMLInputFactory.newInstance()` whose enclosing method parses input without any hardening call first (`setFeature(disallow-doctype-decl)`, `FEATURE_SECURE_PROCESSING`, `setExpandEntityReferences(false)`, or `setProperty(ACCESS_EXTERNAL_*)`) — default config is XXE-vulnerable. |
| `CK-INSECURE-RANDOM` | W | (a) `new Random(<constant seed>)` outside test sources — predictable sequence; (b) `java.util.Random`/`Math.random`/`ThreadLocalRandom` result flowing into a variable or parameter whose name matches the secret-name regex above (token/session/salt generation needs `SecureRandom`). |
| `CK-TRUST-ALL` | E | An `X509TrustManager` implementation whose `checkClientTrusted`/`checkServerTrusted` bodies are empty, or a `HostnameVerifier` (class or lambda) whose `verify` body is the constant `return true;` — disables TLS validation entirely. |
| `CK-PLAIN-HTTP` | I | String literal starting `http://` used to construct `URL`/`URI`/`HttpRequest` — cleartext transport. *(Exempt localhost/`127.0.0.1`/`[::1]`, `.local`/`.test`/`example.com` hosts, and XML-namespace-shaped URLs, which are identifiers, not fetched.)* |
| `CK-NATIVE-DESERIAL` | I | `ObjectInputStream.readObject()` — native Java deserialization is a well-known RCE vector when input isn't fully trusted. Advise an `ObjectInputFilter` allowlist or a different format. |
| `CK-REDOS` | W | Regex **literal** (to `Pattern.compile`, `String.matches/replaceAll/split`) containing a catastrophic-backtracking shape: a quantified group whose body is itself quantified or contains overlapping quantified alternatives — `(a+)+`, `(a*)*`, `(a\|aa)+`, `(\\s*\\w+)*$`. Crafted input makes matching exponential and hangs the thread (ReDoS). Suggest possessive quantifiers (`*+`), atomic groups, or a rewrite. *(Only literal patterns are analyzed; the shape check is a small grammar over the parsed regex, not full worst-case analysis.)* |

### 6.6 Pack `performance`

| ID | Sev | Detection |
|---|---|---|
| `CK-STR-CONCAT-LOOP` | W | Founding rule — §5.6. |
| `CK-REGEX-IN-LOOP` | W | Inside any loop body: `Pattern.compile(<constant>)`, or `String.matches/replaceAll/replaceFirst/split(<constant regex>)` — recompiles the regex every iteration. Suggest a `static final Pattern`. *(`String.split` with a single non-meta character is exempt — the JDK fast-paths it.)* |
| `CK-KEYSET-GET` | W | Enhanced-`for` over `m.keySet()` whose body calls `m.get(key)` on the same map symbol with the loop variable — one hash lookup per entry wasted. Suggest `entrySet()`. |
| `CK-BOXED-ACCUMULATOR` | W | Compound assignment (`+=`, `*=`, …) inside a loop whose LHS is a **boxed** `Integer/Long/Double` variable declared outside the loop — every iteration unboxes, computes, and re-boxes (allocation per iteration). Suggest the primitive type. |
| `CK-CONTAINS-IN-LOOP` | I | `list.contains(...)`/`list.indexOf(...)` on a `List`-typed receiver declared outside the loop, invoked inside a loop — O(n·m) scanning. Suggest building a `HashSet` before the loop. |
| `CK-NEW-WRAPPER` | W | `new Integer/Long/Double/Float/Short/Byte/Character/Boolean(...)` (deprecated-for-removal boxing constructors) and `new String(String)` — pure allocation waste. Suggest `valueOf` / the literal. |

### 6.7 Pack `api-misuse`

| ID | Sev | Detection |
|---|---|---|
| `CK-GENERIC-MISMATCH` | E | Founding rule — §5.9. |
| `CK-IMMUTABLE-MUTATE` | E | Mutator call (`add`, `remove`, `set`, `put`, `clear`, `addAll`, `sort`, …) on a receiver whose value provably originates (same method, direct assignment chain) from `List.of/Set.of/Map.of/Collections.emptyList/emptyMap/unmodifiable*` → guaranteed `UnsupportedOperationException`; or `add/remove` on `Arrays.asList(...)` (fixed-size). |
| `CK-TOARRAY-CAST` | E | Cast of a no-arg `Collection.toArray()` result to any array type other than `Object[]`: `(String[]) list.toArray()` — the method returns `Object[]`, guaranteed `ClassCastException`. Suggest `list.toArray(new String[0])` or `toArray(String[]::new)`. |
| `CK-REGEX-META-LITERAL` | E | `split`/`replaceAll`/`replaceFirst`/`matches` whose regex argument is a literal consisting of a single bare metacharacter (`.` `|` `$` `^` `*` `+` `?` `(` `)` `[` `{` `\`) — `"file.txt".split(".")` returns an **empty array** (`.` matches everything); `(`/`[` throw `PatternSyntaxException` at runtime. Suggest `Pattern.quote(".")` or `"\\."`. |
| `CK-LOCALE-CASE` | I | `toUpperCase()`/`toLowerCase()` (no-arg, default-locale) whose result feeds `equals`/`switch`/`Map` key usage — Turkish-ı breaks case-insensitive protocol comparisons. Suggest `Locale.ROOT` or `equalsIgnoreCase`. |
| `CK-COMPUTE-IF-ABSENT-MOD` | E | Lambda passed to `Map.computeIfAbsent/computeIfPresent/compute/merge` whose body structurally modifies the **same map symbol** (`put`, `remove`, `clear`, another `compute*`) — `HashMap` throws `ConcurrentModificationException` since JDK 9 (and silently corrupted before that). Restructure to compute the value first, then insert. |
| `CK-REMOVE-INT-AMBIGUOUS` | W | `list.remove(intExpr)` on a `List<Integer>` — overload resolution picks `remove(int index)`, not `remove(Object)`: `list.remove(1)` removes the element **at index 1**, not the value `1` (and may throw `IndexOutOfBoundsException`). Use `list.remove(Integer.valueOf(1))` for by-value removal. |
| `CK-IMMUTABLE-FACTORY-NULL` | E | A `null` literal (bare, parenthesized, or cast — `(String) null`) in any argument of `List.of`/`Set.of`/`Map.of`/`Map.ofEntries`/`Map.entry`/`copyOf`, receiver resolving to `java.util.List/Set/Map`. These factories forbid null by contract → guaranteed `NullPointerException`, which in a `static final` initializer surfaces as `ExceptionInInitializerError`/`NoClassDefFoundError` far from the cause. *(Exempt the null-tolerant factories `Arrays.asList`, `Collections.singletonList`, and mutable collections; exempt a non-literal argument that merely happens to be null at runtime — v1 does not track values across methods.)* |
| `CK-SYSPROP-PARSE` | W | `Boolean.getBoolean(x)` / `Integer.getInteger(x)` / `Long.getLong(x)` (receiver resolving to the `java.lang` wrapper) where `x` is not a property name: either a compile-time constant that reads as a **value** (`"true"`, `"false"`, an integer literal), or a **non-constant** expression containing no dotted string literal. These methods take a system-property *name*, not a value — the call silently returns `false`/`null` forever. Suggest `parseBoolean`/`parseInt`/`parseLong`. *(Exempt any argument carrying a dotted string constant — `"acme.debug"`, `"acme.pool." + name` — and any other compile-time constant, e.g. `"timeout"`: those are property names. Residual gap: a genuinely dynamic, undotted property name in a variable is flagged; suppress per site.)* |
| `CK-TOMAP-DUPLICATES` | I | Two-argument `Collectors.toMap(keyFn, valueFn)` — throws `IllegalStateException: Duplicate key` the first time two elements map to the same key, which is typically discovered in production data, not tests. Add the merge function (third argument) or state why keys are unique. *(Exempt when the key function is `identity()` over a `Set` source.)* |

### 6.8 Pack `nullness`

The pack shares two pieces of machinery: the **null-fact collector** built for CK-IMPOSSIBLE-COND, and a **known-nullable method list** shipped as data (`Map.get`, `System.getenv`, `System.getProperty`, `Matcher.group`, `Queue.poll/peek`, `Class.getResource*`, JDBC `ResultSet.getString/…`).

| ID | Sev | Detection |
|---|---|---|
| `CK-IMPOSSIBLE-COND` | W | Founding rule — §5.8. |
| `CK-NON-SHORT-CIRCUIT` | E | Boolean `&`/`\|` (non-short-circuit) where the LHS establishes a null-fact for `x` and the RHS dereferences `x`: `if (x != null & x.length() > 0)` — **both** sides always evaluate, so the guard doesn't guard; guaranteed NPE when `x` is null. Suggest `&&`/`\|\|`. (Reuses the CK-IMPOSSIBLE-COND fact collector.) |
| `CK-UNBOX-NPE` | E | Auto-unboxing applied directly to a nullable-by-contract expression: `int x = map.get(k);` (`Map.get` returns null on miss), or a ternary whose branches are a primitive and `null` (`cond ? 1 : null` unboxed at use). |
| `CK-NULLABLE-CHAIN` | W | Direct member access chained onto a known-nullable-returning call: `map.get(k).run()`, `System.getenv("X").trim()`, `matcher.group(1).length()` — NPE on the miss/absent case, typically the untested path. *(Only direct chains fire; a local variable checked before use does not.)* |
| `CK-OPTIONAL-OF-NULLABLE` | W | `Optional.of(expr)` where `expr` is the `null` literal, a known-nullable call, or a `@Nullable`-typed expression — NPEs exactly when `Optional.ofNullable` was intended. |
| `CK-NULL-TO-NONNULL` | E | `null` literal (or provably-null local) passed as an argument whose parameter is `@NonNull`-annotated **or** unannotated inside a `@NullMarked` scope (JSpecify semantics); also `return null` in such a method. Only fires when JSpecify/JSR-305 annotations are actually present on the target — codekoll performs no nullness inference of its own in v1. |
| `CK-OPTIONAL-GET-BARE` | I | `.get()`/`.orElseThrow()` chained **directly** onto an `Optional`-returning call (`repo.find(id).get()`) with no intervening `isPresent`/`isEmpty` guard in the same statement — collapses Optional back into an unchecked NPE-equivalent. *(Local-variable Optionals are not tracked in v1 — too flow-sensitive; only direct chains fire.)* |
| `CK-OVERRIDE-NULLNESS` | E | An override **weakens the supertype's nullness contract**: a parameter is `@NonNull` (or unannotated in a `@NullMarked` scope) where the overridden method's parameter is `@Nullable`, or the return is `@Nullable` where the overridden return is non-null. Callers dispatching through the supertype hit NPEs the compiler never sees. Fires only when JSpecify/JSR-305 annotations are present on the pair. |

### 6.9 Pack `modern`

Java 21+ platform features are where the incumbent analyzers are thinnest — most of their pattern catalogs predate records, sealed types, virtual threads, structured concurrency, and much of `java.time` in practice. This pack is codekoll's most differentiated coverage (see §1, *Prior art & differentiation*).

| ID | Sev | Detection |
|---|---|---|
| `CK-SEALED-SWITCH-DEFAULT` | W | A `switch` whose scrutinee is a **sealed** interface/class and which contains a `default` branch (or a total `case Object o`) — this defeats the compiler's exhaustiveness checking: when a new permitted subtype is added later, the switch silently routes it to `default` instead of failing compilation. Remove `default` and enumerate the cases. |
| `CK-RECORD-ARRAY-COMPONENT` | W | A `record` declaring an array-typed component — the generated `equals`/`hashCode` compare the array **by reference**, so two records with identical contents are not equal, and `toString` prints `[Ljava...`. Use `List` instead, or override `equals`/`hashCode` with `Arrays.equals`. |
| `CK-RECORD-MUTABLE-COMPONENT` | I | A `record` component typed as a mutable collection (`List/Set/Map` from an unknown source, `Date`, `Calendar`) with **no defensive copy** (`List.copyOf(...)`) in a compact constructor — callers can mutate state the record's users assume immutable. |
| `CK-VT-POOLING` | W | A virtual-thread factory (`Thread.ofVirtual().factory()`) passed to a **bounded** executor (`newFixedThreadPool`, `newSingleThreadExecutor`, custom `ThreadPoolExecutor`) — virtual threads are designed to be cheap and unpooled; pooling caps the concurrency they exist to provide. Use `Executors.newVirtualThreadPerTaskExecutor()`. |
| `CK-VT-DAEMON-PRIORITY` | E | `setDaemon(false)` on a thread known to be virtual (created via `Thread.ofVirtual()`/`startVirtualThread` in the same method) — throws `IllegalArgumentException` at runtime (virtual threads are always daemons). `setPriority(...)` on one is flagged W — it is silently ignored. |
| `CK-STRUCTURED-GET-BEFORE-JOIN` | E | Inside a `StructuredTaskScope` try block: `Subtask.get()` invoked, in statement order, **before** the scope's `join()` — throws `IllegalStateException` every time. Move the `get()` after `join()`. |
| `CK-STREAM-REUSE` | E | Two terminal operations invoked on the **same `Stream`-typed local variable** in statement order within a method — the second throws `IllegalStateException: stream has already been operated upon or closed`. Re-create the stream or collect once and reuse the collection. |
| `CK-CHRONO-UNSUPPORTED` | E | `Instant.plus/minus(n, ChronoUnit.MONTHS/YEARS/WEEKS)` or `LocalDate.plus/minus(n, ChronoUnit.HOURS/MINUTES/SECONDS)` with a constant unit — compiles cleanly, **always** throws `UnsupportedTemporalTypeException`. Convert to `ZonedDateTime`/`LocalDateTime` first, or use the unit-specific methods. |
| `CK-DURATION-CALENDAR` | I | `ZonedDateTime.plus(Duration.ofDays(n))` — `Duration` is exact seconds, so across a DST transition "+1 day" lands at the wrong wall-clock time. Use `plusDays(n)` / `Period.ofDays(n)` for calendar arithmetic. |
| `CK-ARENA-USE-AFTER-CLOSE` | E | FFM API (Java 22+): a `MemorySegment` obtained from an `Arena` used, in statement order, **after** the arena's `close()` — or a segment from an `Arena` opened in try-with-resources escaping the try block (returned/stored) — throws `IllegalStateException: already closed` (or is a use-after-free caught by the runtime). Keep segment use inside the arena's scope. |

### 6.10 Pack `frameworks`

**Silently ignored code** — the sneakiest bug class in this catalog: these compile, deploy, and run **without any error**; the annotation or contract just quietly doesn't apply. Nothing throws, so nothing appears in logs — the transaction isn't there when you need the rollback, the test never actually ran. Generic analyzers barely touch this class.

Scope note: these rules match annotations by qualified name where the framework is on `--classpath`, degrading to simple-name matching when it isn't (attribution tolerance). The whole pack self-disables at zero cost when a compilation unit contains no framework annotations. This is annotation-**shape** checking against documented framework contracts — codekoll does not model framework runtime semantics.

| ID | Sev | Detection |
|---|---|---|
| `CK-PROXY-ANNOTATION-INVISIBLE` | E | `@Transactional`, `@Cacheable`, `@Async`, `@Retryable`, or `@Scheduled` on a **private, final, or static** method — Spring's proxy mechanism cannot intercept these, so the annotation is silently ignored: no transaction, no cache, runs synchronously. Make the method public and non-final, or move it to a collaborator bean. |
| `CK-PROXY-SELF-INVOKE` | W | A method calls another method **of the same class** that carries `@Transactional`/`@Async`/`@Cacheable` via plain `this`-dispatch — the call never crosses the proxy, so the annotation silently doesn't apply on this path (class-local analysis). Inject the bean into itself or extract the annotated method to another bean. |
| `CK-INJECT-STATIC` | E | `@Autowired`, `@Value`, `@Inject`, or `@PersistenceContext` on a **static** field — DI containers skip static fields silently; it stays `null` and NPEs at first use (or worse, never used and misleads readers). |
| `CK-ENTITY-CONTRACT` | E | A JPA `@Entity`/`@Embeddable` class that is `final` or lacks a no-arg constructor (default or explicit, non-private) — Hibernate/JPA proxying and hydration fail at runtime with provider-specific errors far from this class. `final` methods on an `@Entity` flagged W (breaks lazy-loading proxies). |
| `CK-TEST-INVISIBLE` | E | A `@Test`/`@ParameterizedTest`/`@RepeatedTest` method that is **private or static or returns non-void** — JUnit silently skips it (JUnit 4) or fails discovery quietly in common setups (JUnit 5): the test reports green *by never running*. Also flags `@Test` on a class with no test engine visible. |
| `CK-SLF4J-PLACEHOLDER` | E | SLF4J/Log4j2 logging call with a **constant** format string: count `{}` placeholders vs arguments, honoring the trailing-`Throwable` convention — a mismatch silently truncates the message or drops arguments. (Reuses the CK-FORMAT-MISMATCH parsing machinery.) |
| `CK-LOG-EXCEPTION-LOST` | W | In a `catch` block, a logging call that includes the caught exception only via string concatenation or `e.getMessage()` — the **stack trace is lost**; `getMessage()` is frequently null. Pass the exception as the final argument: `log.error("Payment failed for {}", orderId, e);`. |

---

## 7. Reporting

### Console (default)
```
src/main/java/com/acme/JobService.java:42:16  ERROR  CK-REF-EQUALITY
    if (status == "ACTIVE") {
               ^^
  `==` compares references, not contents. Use .equals().

✖ 3 errors, 5 warnings in 214 files (1.8s)
```
Color when TTY; `--no-color` / `NO_COLOR` respected. `--explain CK-…` prints a rule's full `explanation()` + `fix()` with a before/after snippet drawn from its example class.

### SARIF 2.1.0
Full rule metadata (`rules` array with `description`/`explanation`/`fix` mapped to `shortDescription`/`fullDescription`/`help`, pack as a rule tag), one `result` per finding with `physicalLocation` + region. Consumed by GitHub code scanning, VS Code SARIF viewer, CI annotators.

### JSON
Flat array of `Finding` records, stable schema, for custom tooling.

---

## 8. Precision Policy

Every rule ships with an explicit false-positive budget: **a rule that cries wolf gets disabled by users and is worth less than no rule.** Concretely:
- Prefer *missing* a real bug (false negative) over flagging correct code.
- Every exemption listed in §5–§6 is part of the rule's contract and has a dedicated test.
- INFO severity exists precisely for the heuristic rules (`CK-OPTIONAL-GET-BARE`, `CK-FLOAT-EQUALITY`, `CK-CONTAINS-IN-LOOP`, …) whose precision is inherently lower — they never affect exit codes at the default `--fail-on error`.
- Rules report **why** the code is wrong and **what to do**, in one sentence each — sourced from `explanation()`/`fix()` metadata.

## 9. Testing Strategy

- **Fixture tests (primary):** each rule has a directory of `.java` fixtures:
  - `positive/` — findings marked with `// :: finding-here` comments; the harness compiles the fixture in-memory (`JavaFileObject` from string, `JavacTask` with `--release 25`), runs the single rule, and asserts findings appear exactly at marked lines — no more, no fewer.
  - `negative/` — exemption cases that must produce **zero** findings (this is where the precision policy is enforced).
- **Integration tests:** run the full CLI against a small sample project; snapshot-assert the SARIF output.
- **Example verification:** the `codekoll-examples` module (see PLAN.md) proves every registered rule fires on realistic, documented example code and stays silent on the corrected variant — a registry-completeness test makes shipping a rule without an example impossible, and a metadata test rejects any rule with an empty `explanation()` or `fix()`.
- **Self-hosting smoke test:** codekoll runs on its own source in CI with `--fail-on error`.
- **Load tests:** the `codekoll-load-test` module measures CPU time and peak heap over checked-in and deterministically generated corpora; a quick profile runs on every CI build and fails on regression against a committed baseline (see §10 and PLAN Milestone 9).
- **Corpus regression (post-v1):** run against 2–3 large OSS codebases, review every finding manually once, then pin the count.

## 10. Performance Targets

- 100 kLOC project analyzed in < 30 s on a laptop (javac attribution dominates; rules are single-pass visitors).
- With ~105 rules, per-rule traversal passes are not viable: the engine dispatches **one combined traversal** that feeds every enabled rule's node-kind subscriptions (each rule declares the `Tree.Kind`s it cares about), rather than 105 full passes.
- Memory: bounded by javac's own footprint for the compilation; findings stream to the reporter.
- **These targets are enforced, not aspirational:** the `codekoll-load-test` module (PLAN Milestone 9) runs a quick CPU/memory profile on every CI build against a committed `baseline.json` (baseline v0 = the project state when the harness landed) and fails the build on > 15 % CPU-time or > 20 % peak-heap regression. Per-version PNG/SVG charts (CPU, heap, kLOC/s throughput + cross-version trend) are generated into `docs/perf/` on each release.

## 11. Future Work (explicitly out of v1)

- Intra-method dataflow (would upgrade CK-RESOURCE-LEAK, CK-IMPOSSIBLE-COND, CK-OPTIONAL-GET-BARE, CK-NULLABLE-CHAIN, CK-IMMUTABLE-MUTATE from heuristic to path-sensitive).
- Full JSpecify propagation (`@Nullable` flow through locals) — natural extension of the nullness pack's fact machinery.
- Gradle/Maven plugins wrapping the CLI.
- Incremental analysis (hash-based per-file caching).
- Auto-fix suggestions as SARIF `fixes` (the `fix()` metadata is the seed).
- `jlink` runtime image distribution (module graph already supports it).
