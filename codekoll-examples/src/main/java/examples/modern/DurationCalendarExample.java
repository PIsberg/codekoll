package examples.modern;

import java.time.Duration;
import java.time.ZonedDateTime;

/**
 * Example for rule {@code CK-DURATION-CALENDAR}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(ZonedDateTime)} schedules "same time tomorrow"
 * by adding {@code Duration.ofDays(1)} to a zoned time.
 *
 * <p><b>What happens at runtime:</b> Duration is exactly 86 400 seconds, but a calendar day
 * across a daylight-saving transition is 23 or 25 hours. Twice a year the "daily 09:00
 * report" silently shifts to 08:00 or 10:00 — and stays shifted until the next transition.
 *
 * <p><b>How to fix it:</b> calendar arithmetic with calendar units, as
 * {@link #fixed(ZonedDateTime)} does.
 */
public class DurationCalendarExample {

  public ZonedDateTime buggy(ZonedDateTime lastRun) {
    return lastRun.plus(Duration.ofDays(1)); // :: CK-DURATION-CALENDAR
  }

  public ZonedDateTime fixed(ZonedDateTime lastRun) {
    return lastRun.plusDays(1);
  }
}
