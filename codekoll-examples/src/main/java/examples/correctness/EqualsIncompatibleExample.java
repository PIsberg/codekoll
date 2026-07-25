package examples.correctness;

/**
 * Example for rule {@code CK-EQUALS-INCOMPATIBLE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Long, Integer)} compares a {@code Long} id against
 * an {@code Integer}.
 *
 * <p><b>What happens at runtime:</b> {@code Long.equals} returns false for anything that
 * is not a Long — an Integer of the same numeric value is never equal. The id lookup
 * silently never matches, so the record "isn't found" even though it is right there.
 *
 * <p><b>How to fix it:</b> compare same-typed values, as {@link #fixed(Long, Integer)}
 * does.
 */
public class EqualsIncompatibleExample {

  public boolean buggy(Long recordId, Integer candidateId) {
    return recordId.equals(candidateId); // :: CK-EQUALS-INCOMPATIBLE
  }

  public boolean fixed(Long recordId, Integer candidateId) {
    return recordId.equals(candidateId.longValue());
  }
}
