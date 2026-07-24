package io.codekoll.api;

/** Severity of a finding. INFO never affects exit codes at the default {@code --fail-on error}. */
public enum Severity {
  ERROR,
  WARNING,
  INFO
}
