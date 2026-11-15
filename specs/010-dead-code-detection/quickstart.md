# Quickstart: manual checks for Dead Code Detection

Two success criteria need a person: SC-002 is a maintainer's judgement and SC-003 a timing
comparison the CI quick profile is too coarse for (it fails only on a doubling), measured as
wall-clock time from the shell because codekoll prints no time. T018 runs both
after T017 and records the results under a `## Results` heading it adds at the end, from the repository root, on a built jar (`mvn -DskipTests package`).

## SC-002: no false positives on codekoll's own sources

```bash
java -jar codekoll-cli/target/codekoll.jar --packs dead-code --resolve none .
```

Read every finding. A false positive is fixed in the rule, with a negative fixture in its
`*RuleTest` (Constitution Principle III), before the feature merges. Pass: zero findings judged
false positives. True positives in codekoll's own code are not part of this feature.

## SC-003: at most 10% added time

Same jar, same machine, nothing else building. Write `no-dead-code.toml` in a scratch directory:

```toml
[rules]
disable-packs = ["dead-code"]
```

The console summary prints only counts, so measure wall-clock time from the shell (bash `time`,
or `date +%s%N` before and after). Run each command once as a warm-up, then 5 times, and take
the median of those 5 per command:

```bash
time java -jar codekoll-cli/target/codekoll.jar --resolve none --fail-on never --config <scratch>/no-dead-code.toml .
time java -jar codekoll-cli/target/codekoll.jar --resolve none --fail-on never .
```

Pass: the ratio enabled/disabled (second median over first) is at most 1.10.

## Results

Run 2026-11-15 on `codekoll-cli/target/codekoll.jar` (rebuilt with `mvn -DskipTests package`), repository root.

SC-002: `--packs dead-code --resolve none .` reports 4 findings, all in `codekoll-examples/src/main/java/examples/deadcode/`, each on its `// :: <ID>` marker in a `buggy()` part: CK-DEAD-BRANCH (DeadBranchExample:20), CK-UNREACHABLE (UnreachableExample:22), CK-UNUSED-LOCAL (UnusedLocalExample:16), CK-UNUSED-PRIVATE (UnusedPrivateExample:17). 4 true positives, 0 false positives. No finding in any other source. Pass.

SC-003: wall-clock via `date +%s%N`, one warm-up then 5 runs per command, `--resolve none --fail-on never .`.

| Pack | Runs (ms) | Median (ms) |
|------|-----------|-------------|
| disabled (`no-dead-code.toml`) | 11846 11690 11796 11910 12210 | 11846 |
| enabled | 12223 11962 11726 11726 12697 | 11962 |

Ratio enabled/disabled = 11962 / 11846 = 1.01 (limit 1.10). Pass. Run spread (11690 to 12697 ms) is larger than the median gap, so the difference is within noise.
