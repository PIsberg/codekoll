package examples.correctness;

import java.time.format.DateTimeFormatter;

/**
 * Example for rule {@code CK-WEEK-YEAR-FORMAT}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} builds a date formatter with {@code "YYYY-MM-dd"}
 * — uppercase YYYY.
 *
 * <p><b>What happens at runtime:</b> YYYY is the ISO <em>week-based</em> year. It matches
 * the calendar year for ~360 days — then December 29–31 format as NEXT year: 2024-12-30
 * prints as <b>2025</b>-12-30. Invoices, filenames and partitions dated with it silently
 * jump a year every New Year's week; no test run outside that week can catch it.
 *
 * <p><b>How to fix it:</b> lowercase {@code yyyy}, as {@link #fixed()} does.
 */
public class WeekYearFormatExample {

  public DateTimeFormatter buggy() {
    return DateTimeFormatter.ofPattern("YYYY-MM-dd"); // :: CK-WEEK-YEAR-FORMAT
  }

  public DateTimeFormatter fixed() {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd");
  }
}
