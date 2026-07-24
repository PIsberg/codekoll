package io.codekoll.api;

import java.nio.file.Path;

/**
 * One detected problem instance.
 *
 * @param rule the reporting rule
 * @param severity effective severity (default from the rule, possibly overridden by config)
 * @param file source file the finding is in
 * @param line 1-based line number
 * @param column 1-based column number
 * @param message human-oriented message: why it is wrong + what to do
 * @param snippet offending source excerpt (single line, trimmed)
 */
public record Finding(
    RuleId rule,
    Severity severity,
    Path file,
    long line,
    long column,
    String message,
    String snippet) {}
