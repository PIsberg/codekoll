# Codekoll rule catalog

Generated from rule metadata — 107 rules.

## correctness (26)

| Rule | Severity | What is wrong |
|------|----------|---------------|
| `CK-ARRAY-OBJECT-METHODS` | ERROR | equals/hashCode/toString on an array uses identity, not contents |
| `CK-ASSERT-SIDE-EFFECT` | WARNING | State changed inside an assert — skipped entirely when assertions are disabled |
| `CK-ASSIGN-IN-COND` | WARNING | Assignment (=) used directly as a boolean condition |
| `CK-BIGDECIMAL-DOUBLE` | WARNING | new BigDecimal(double) carries binary imprecision into exact arithmetic |
| `CK-BIGDECIMAL-EQUALS` | INFO | BigDecimal.equals is scale-sensitive (1.0 != 1.00) |
| `CK-COLLECTION-SELF-ADD` | ERROR | Collection added to itself (self-referential structure) |
| `CK-DEFAULT-CHARSET` | WARNING | Byte/char conversion relying on the platform default charset |
| `CK-EQUALS-HASHCODE` | WARNING | equals without hashCode (or hashCode without equals) |
| `CK-EQUALS-INCOMPATIBLE` | ERROR | equals() between provably unrelated types (always false) |
| `CK-EQUALS-NULL-ARG` | ERROR | equals(null) always returns false by contract |
| `CK-EQUALS-OVERLOAD` | WARNING | equals(SpecificType) that overloads instead of overriding equals(Object) |
| `CK-EXCEPTION-NOT-THROWN` | ERROR | Exception constructed but never thrown |
| `CK-FORMAT-MISMATCH` | ERROR | Format string conversions do not match the arguments |
| `CK-IGNORED-RETURN` | WARNING | Return value of a pure method on an immutable type is discarded |
| `CK-INFINITE-RECURSION` | ERROR | Method unconditionally calls itself (infinite recursion) |
| `CK-ITERATOR-DOUBLE-NEXT` | WARNING | Loop guarded by hasNext() calls next() twice in one iteration |
| `CK-NAN-COMPARE` | ERROR | == or != against Double.NaN is constant by IEEE 754 |
| `CK-OPTIONAL-NULL` | ERROR | return null from an Optional-returning method |
| `CK-REF-EQUALITY` | ERROR | == or != on String/boxed types compares references, not values |
| `CK-SB-CHAR-CTOR` | ERROR | new StringBuilder(char) sets capacity, not content |
| `CK-SELF-ASSIGN` | ERROR | Variable assigned to itself |
| `CK-SELF-COMPARE` | ERROR | Expression compared with itself (constant result) |
| `CK-SWITCH-FALLTHROUGH` | INFO | Switch case falls through without break |
| `CK-TOSTRING-ARRAY` | WARNING | Array where an implicit toString() is triggered |
| `CK-URL-EQUALS` | WARNING | equals/hashCode on java.net.URL (blocking DNS, surprising equality) |
| `CK-WEEK-YEAR-FORMAT` | ERROR | YYYY (week-based year) used in a calendar date pattern |

## numeric (8)

| Rule | Severity | What is wrong |
|------|----------|---------------|
| `CK-ABS-OVERFLOW` | WARNING | Math.abs of hashCode()/nextInt() can still be negative |
| `CK-COMPARE-SUBTRACT` | WARNING | Comparator implemented by int subtraction (overflow breaks ordering) |
| `CK-DIV-ZERO` | ERROR | Integer division or modulo by literal zero |
| `CK-FLOAT-EQUALITY` | INFO | Exact == between floating-point values |
| `CK-INT-DIV-FLOAT` | WARNING | Integer division assigned to a floating-point variable |
| `CK-INT-OVERFLOW-WIDEN` | WARNING | int multiplication widened to long AFTER it overflows |
| `CK-OCTAL-LITERAL` | WARNING | Integer literal with a leading zero is octal, not decimal |
| `CK-SHIFT-OOB` | ERROR | Shift distance >= width of the shifted type |

## concurrency (11)

| Rule | Severity | What is wrong |
|------|----------|---------------|
| `CK-CONCURRENT-MOD` | ERROR | Collection modified while iterating it with for-each |
| `CK-CTOR-THREAD-START` | WARNING | Thread started from inside a constructor |
| `CK-DCL-NO-VOLATILE` | WARNING | Double-checked locking on a non-volatile field |
| `CK-LOCK-NO-FINALLY` | WARNING | Lock.lock() without try/finally unlock |
| `CK-MONITOR-ON-LOCK` | ERROR | synchronized on a java.util.concurrent Lock object |
| `CK-SLEEP-IN-SYNC` | INFO | Thread.sleep while holding a monitor |
| `CK-STATIC-DATEFORMAT` | ERROR | Static SimpleDateFormat/Calendar/NumberFormat shared across threads |
| `CK-SYNC-ON-VALUE` | ERROR | synchronized on a String or boxed primitive (shared/interned lock) |
| `CK-THREAD-RUN` | ERROR | Thread.run() called directly instead of start() |
| `CK-VOLATILE-COMPOUND` | WARNING | Compound update (++/--/+=) on a volatile field is not atomic |
| `CK-WAIT-NO-LOOP` | WARNING | wait()/await() not guarded by a loop |

## resources (9)

| Rule | Severity | What is wrong |
|------|----------|---------------|
| `CK-CATCH-BROAD` | INFO | catch (Throwable/Error) also swallows JVM errors |
| `CK-CATCH-NPE` | WARNING | Explicit catch of NullPointerException |
| `CK-EMPTY-CATCH` | WARNING | Empty catch block silently swallows the exception |
| `CK-FINALIZE` | WARNING | finalize() override (deprecated for removal, unreliable cleanup) |
| `CK-LOST-CAUSE` | WARNING | Rethrow drops the original exception (no cause) |
| `CK-PRINT-STACKTRACE` | INFO | printStackTrace() bypasses the logging framework |
| `CK-RESOURCE-LEAK` | WARNING | AutoCloseable created but never closed |
| `CK-SYSTEM-EXIT` | INFO | System.exit in non-launcher code |
| `CK-THROW-IN-FINALLY` | WARNING | throw or return inside finally discards the in-flight exception |

## security (11)

| Rule | Severity | What is wrong |
|------|----------|---------------|
| `CK-CRYPTO-WEAK` | ERROR | Weak or broken cryptographic algorithm requested via getInstance(...) |
| `CK-EXEC-CONCAT` | ERROR | Shell command built by concatenating variables |
| `CK-HARDCODED-SECRET` | WARNING | Secret material hardcoded in a string literal |
| `CK-INSECURE-RANDOM` | WARNING | java.util.Random with a constant seed, or used for secret material |
| `CK-NATIVE-DESERIAL` | INFO | Native Java deserialization (readObject) — classic RCE vector |
| `CK-PLAIN-HTTP` | INFO | http:// URL built for network use (cleartext transport) |
| `CK-REDOS` | WARNING | Regex with catastrophic-backtracking shape (ReDoS) |
| `CK-SQL-CONCAT` | ERROR | SQL built by concatenating variables into the statement |
| `CK-TRUST-ALL` | ERROR | Trust-all TrustManager / always-true HostnameVerifier |
| `CK-WEAK-TLS` | ERROR | SSLContext requested with a broken TLS/SSL protocol version |
| `CK-XXE-FACTORY` | WARNING | XML parser factory created without XXE hardening |

## performance (6)

| Rule | Severity | What is wrong |
|------|----------|---------------|
| `CK-BOXED-ACCUMULATOR` | WARNING | Boxed numeric accumulator updated inside a loop |
| `CK-CONTAINS-IN-LOOP` | INFO | List.contains/indexOf called inside a loop (O(n*m)) |
| `CK-KEYSET-GET` | WARNING | Iterating keySet() then calling get(key) — use entrySet() |
| `CK-NEW-WRAPPER` | WARNING | Boxing constructor (new Integer) or new String(String) |
| `CK-REGEX-IN-LOOP` | WARNING | Constant regex compiled inside a loop |
| `CK-STR-CONCAT-LOOP` | WARNING | String concatenation accumulating across loop iterations |

## api-misuse (11)

| Rule | Severity | What is wrong |
|------|----------|---------------|
| `CK-COMPUTE-IF-ABSENT-MOD` | ERROR | Map modified inside its own computeIfAbsent lambda |
| `CK-GENERIC-MISMATCH` | ERROR | Argument type can never match the collection's key/element type |
| `CK-IMMUTABLE-FACTORY-DUPLICATE` | ERROR | Duplicate constant element in Set.of or duplicate key in Map.of — always throws |
| `CK-IMMUTABLE-FACTORY-NULL` | ERROR | null passed to List.of/Set.of/Map.of — these factories reject null and always throw |
| `CK-IMMUTABLE-MUTATE` | ERROR | Mutating an immutable/fixed-size collection |
| `CK-LOCALE-CASE` | INFO | toUpperCase/toLowerCase without an explicit Locale |
| `CK-REGEX-META-LITERAL` | ERROR | split/replaceAll with a bare regex metacharacter literal |
| `CK-REMOVE-INT-AMBIGUOUS` | WARNING | List<Integer>.remove(int) removes by INDEX, not by value |
| `CK-SYSPROP-PARSE` | WARNING | Boolean.getBoolean/Integer.getInteger/Long.getLong read a system property, they do not parse their argument |
| `CK-TOARRAY-CAST` | ERROR | Cast of no-arg toArray() to a specific array type |
| `CK-TOMAP-DUPLICATES` | INFO | Collectors.toMap without a merge function throws on duplicate keys |

## nullness (8)

| Rule | Severity | What is wrong |
|------|----------|---------------|
| `CK-IMPOSSIBLE-COND` | WARNING | Null-check chain that contradicts itself (dead branch or guaranteed NPE) |
| `CK-NON-SHORT-CIRCUIT` | ERROR | Non-short-circuit &/| defeats a null guard |
| `CK-NULL-TO-NONNULL` | ERROR | null passed to (or returned as) a @NonNull |
| `CK-NULLABLE-CHAIN` | WARNING | Method call chained onto a known-nullable result |
| `CK-OPTIONAL-GET-BARE` | INFO | Optional.get() chained directly onto the producing call |
| `CK-OPTIONAL-OF-NULLABLE` | WARNING | Optional.of(possibly-null) instead of ofNullable |
| `CK-OVERRIDE-NULLNESS` | ERROR | Override weakens the supertype's nullness contract |
| `CK-UNBOX-NPE` | ERROR | Auto-unboxing of a nullable Map.get/poll result |

## modern (10)

| Rule | Severity | What is wrong |
|------|----------|---------------|
| `CK-ARENA-USE-AFTER-CLOSE` | ERROR | MemorySegment used after its Arena is closed |
| `CK-CHRONO-UNSUPPORTED` | ERROR | Instant.plus(MONTHS) / LocalDate.plus(HOURS): always throws |
| `CK-DURATION-CALENDAR` | INFO | Duration.ofDays used for calendar arithmetic on zoned times |
| `CK-RECORD-ARRAY-COMPONENT` | WARNING | Record component of array type breaks generated equals/hashCode |
| `CK-RECORD-MUTABLE-COMPONENT` | INFO | Record component of a mutable type with no defensive copy |
| `CK-SEALED-SWITCH-DEFAULT` | WARNING | default branch in a switch over a sealed type |
| `CK-STREAM-REUSE` | ERROR | Stream consumed by a second terminal operation |
| `CK-STRUCTURED-GET-BEFORE-JOIN` | ERROR | Subtask.get() called before StructuredTaskScope.join() |
| `CK-VT-DAEMON-PRIORITY` | ERROR | setDaemon(false)/setPriority on a virtual thread |
| `CK-VT-POOLING` | WARNING | Virtual-thread factory used with a pooled/bounded executor |

## frameworks (7)

| Rule | Severity | What is wrong |
|------|----------|---------------|
| `CK-ENTITY-CONTRACT` | ERROR | JPA @Entity that is final or has no no-arg constructor |
| `CK-INJECT-STATIC` | ERROR | @Autowired/@Value/@Inject on a static field is skipped by DI |
| `CK-LOG-EXCEPTION-LOST` | WARNING | Exception logged via string concat / getMessage() loses the stack trace |
| `CK-PROXY-ANNOTATION-INVISIBLE` | ERROR | @Transactional/@Async/@Cacheable on a private, final, or static method |
| `CK-PROXY-SELF-INVOKE` | WARNING | Self-invocation of a @Transactional/@Async method bypasses the proxy |
| `CK-SLF4J-PLACEHOLDER` | ERROR | SLF4J {} placeholder count does not match the arguments |
| `CK-TEST-INVISIBLE` | ERROR | @Test method that is private, static, or returns a value |

