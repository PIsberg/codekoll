package examples.concurrency;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Example for rule {@code CK-STATIC-DATEFORMAT}.
 *
 * <p><b>What is wrong:</b> {@code buggy} formats dates through a {@code static}
 * {@code SimpleDateFormat} shared by every thread.
 *
 * <p><b>What happens at runtime:</b> SimpleDateFormat keeps mutable state; concurrent
 * requests interleave it. Dates come out silently wrong (a timestamp from one request mixed
 * into another) or parsing throws sporadic exceptions — load-dependent corruption that
 * never reproduces on a developer machine.
 *
 * <p><b>How to fix it:</b> the immutable, thread-safe {@code DateTimeFormatter}, as
 * {@code fixed} does.
 */
public class StaticDateFormatExample {

  private static final SimpleDateFormat BUGGY_FORMAT = // :: CK-STATIC-DATEFORMAT
      new SimpleDateFormat("yyyy-MM-dd");

  private static final DateTimeFormatter FIXED_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");

  public String buggy(Date date) {
    return BUGGY_FORMAT.format(date);
  }

  public String fixed(LocalDate date) {
    return FIXED_FORMAT.format(date);
  }
}
