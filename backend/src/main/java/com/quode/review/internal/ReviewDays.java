package com.quode.review.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Day boundaries for one moment in the user's time zone: "today" is the local date of that moment,
 * and due dates are the start of a local day, as instants (stored in UTC).
 */
record ReviewDays(LocalDate today, ZoneId zone) {

  static ReviewDays of(Instant moment, ZoneId zone) {
    return new ReviewDays(LocalDate.ofInstant(moment, zone), zone);
  }

  Instant startOfToday() {
    return today.atStartOfDay(zone).toInstant();
  }

  Instant startOfTomorrow() {
    return dueAt(1);
  }

  /** The start of the day {@code days} after today. */
  Instant dueAt(int days) {
    return today.plusDays(days).atStartOfDay(zone).toInstant();
  }

  /** Due today or earlier: strictly before the start of tomorrow. A new question is never due. */
  boolean isDue(Instant dueAt) {
    return dueAt != null && dueAt.isBefore(startOfTomorrow());
  }
}
