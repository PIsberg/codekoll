package examples.modern;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Example for rule {@code CK-CHRONO-UNSUPPORTED}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Instant)} computes a subscription renewal with
 * {@code instant.plus(1, ChronoUnit.MONTHS)}.
 *
 * <p><b>What happens at runtime:</b> an Instant is a point on the physical timeline — it
 * has no calendar, and "one month" has no fixed physical length. The call compiles cleanly
 * and throws {@code UnsupportedTemporalTypeException: Unsupported unit: Months} on
 * <em>every</em> execution.
 *
 * <p><b>How to fix it:</b> do calendar arithmetic in a zoned context, as
 * {@link #fixed(Instant)} does.
 */
public class ChronoUnsupportedExample {

  public Instant buggy(Instant subscribedAt) {
    return subscribedAt.plus(1, ChronoUnit.MONTHS); // :: CK-CHRONO-UNSUPPORTED
  }

  public Instant fixed(Instant subscribedAt) {
    return subscribedAt.atZone(ZoneId.of("UTC")).plusMonths(1).toInstant();
  }
}
