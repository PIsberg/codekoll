/**
 * Built-in codekoll rule packs. Exports nothing: rules are reached only through the
 * {@link io.codekoll.api.Rule} service.
 */
module io.codekoll.rules {
  requires io.codekoll.api;
  requires static org.jspecify;

  provides io.codekoll.api.Rule with
      io.codekoll.rules.resources.EmptyCatchRule,
      io.codekoll.rules.resources.ResourceLeakRule,
      io.codekoll.rules.resources.PrintStackTraceRule,
      io.codekoll.rules.resources.FinalizeRule,
      io.codekoll.rules.resources.ThrowInFinallyRule,
      io.codekoll.rules.resources.CatchBroadRule,
      io.codekoll.rules.concurrency.ThreadRunRule,
      io.codekoll.rules.concurrency.SyncOnValueRule,
      io.codekoll.rules.concurrency.MonitorOnLockRule,
      io.codekoll.rules.concurrency.VolatileCompoundRule,
      io.codekoll.rules.concurrency.StaticDateFormatRule,
      io.codekoll.rules.concurrency.SleepInSyncRule,
      io.codekoll.rules.security.WeakCryptoRule,
      io.codekoll.rules.security.HardcodedSecretRule,
      io.codekoll.rules.security.WeakTlsRule,
      io.codekoll.rules.security.InsecureRandomRule,
      io.codekoll.rules.security.PlainHttpRule,
      io.codekoll.rules.security.NativeDeserialRule,
      io.codekoll.rules.correctness.IgnoredReturnRule,
      io.codekoll.rules.correctness.RefEqualityRule,
      io.codekoll.rules.correctness.SelfAssignRule,
      io.codekoll.rules.correctness.SelfCompareRule,
      io.codekoll.rules.correctness.EqualsNullArgRule,
      io.codekoll.rules.correctness.ExceptionNotThrownRule,
      io.codekoll.rules.correctness.OptionalNullRule,
      io.codekoll.rules.correctness.SbCharCtorRule,
      io.codekoll.rules.correctness.NanCompareRule,
      io.codekoll.rules.correctness.ArrayObjectMethodsRule,
      io.codekoll.rules.numeric.ShiftOobRule,
      io.codekoll.rules.numeric.DivZeroRule,
      io.codekoll.rules.numeric.CompareSubtractRule,
      io.codekoll.rules.numeric.IntDivFloatRule,
      io.codekoll.rules.numeric.AbsOverflowRule,
      io.codekoll.rules.performance.StrConcatLoopRule,
      io.codekoll.rules.nullness.ImpossibleCondRule,
      io.codekoll.rules.apimisuse.GenericMismatchRule;
}
