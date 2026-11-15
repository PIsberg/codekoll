# Quickstart: manual checks for Dead Code Detection

Two success criteria need a person: SC-002 is a maintainer's judgement and SC-003 a timing
comparison the CI quick profile is too coarse for (it fails only on a doubling). Run both after
T017, from the repository root, on a built jar (`mvn -DskipTests package`).

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

Run each command three times and take the median of the time the console summary prints:

```bash
java -jar codekoll-cli/target/codekoll.jar --resolve none --fail-on never --config <scratch>/no-dead-code.toml .
java -jar codekoll-cli/target/codekoll.jar --resolve none --fail-on never .
```

Pass: the second median is at most 1.10 times the first.
