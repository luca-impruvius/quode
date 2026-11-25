package com.quode.review.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Day boundaries in the user's time zone (docs/specs/M3-daily-review.md, Scheduling): due dates are
 * the start of a local day, stored in UTC. Bucharest is UTC+2 in winter and UTC+3 in summer; its
 * clocks change at 03:00 / 04:00 local time, so local midnight always exists.
 */
class ReviewDaysTests {

  private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");

  private static ReviewDays at(String instant) {
    return ReviewDays.of(Instant.parse(instant), BUCHAREST);
  }

  @Test
  void todayIsTheLocalDateNotTheUtcOne() {
    // 22:30 UTC on 14 January is 00:30 on 15 January in Bucharest.
    ReviewDays days = at("2026-01-14T22:30:00Z");
    assertThat(days.startOfToday()).isEqualTo(Instant.parse("2026-01-14T22:00:00Z"));
    assertThat(days.startOfTomorrow()).isEqualTo(Instant.parse("2026-01-15T22:00:00Z"));
  }

  @Test
  void justBeforeLocalMidnightIsStillTheDayBefore() {
    ReviewDays days = at("2026-01-14T21:59:59Z");
    assertThat(days.startOfToday()).isEqualTo(Instant.parse("2026-01-13T22:00:00Z"));
    assertThat(days.dueAt(1)).isEqualTo(Instant.parse("2026-01-14T22:00:00Z"));
  }

  @Test
  void answersMinutesApartAcrossMidnightAreDueADayApart() {
    assertThat(at("2026-01-14T21:50:00Z").dueAt(1))
        .isEqualTo(Instant.parse("2026-01-14T22:00:00Z"));
    assertThat(at("2026-01-14T22:10:00Z").dueAt(1))
        .isEqualTo(Instant.parse("2026-01-15T22:00:00Z"));
  }

  @Test
  void aDueDateAcrossTheSpringChangeIsLocalMidnightInSummerTime() {
    // Clocks go forward on 29 March 2026; 30 March starts at 21:00 UTC the day before.
    ReviewDays days = at("2026-03-28T10:00:00Z");
    assertThat(days.dueAt(2)).isEqualTo(Instant.parse("2026-03-29T21:00:00Z"));
    assertThat(days.dueAt(1)).isEqualTo(Instant.parse("2026-03-28T22:00:00Z"));
  }

  @Test
  void theDayOfTheSpringChangeIs23HoursLong() {
    ReviewDays days = at("2026-03-29T12:00:00Z");
    assertThat(days.startOfToday()).isEqualTo(Instant.parse("2026-03-28T22:00:00Z"));
    assertThat(days.startOfTomorrow()).isEqualTo(Instant.parse("2026-03-29T21:00:00Z"));
  }

  @Test
  void aDueDateAcrossTheAutumnChangeIsLocalMidnightInWinterTime() {
    // Clocks go back on 25 October 2026; 26 October starts at 22:00 UTC the day before.
    ReviewDays days = at("2026-10-24T10:00:00Z");
    assertThat(days.dueAt(1)).isEqualTo(Instant.parse("2026-10-24T21:00:00Z"));
    assertThat(days.dueAt(2)).isEqualTo(Instant.parse("2026-10-25T22:00:00Z"));
    assertThat(at("2026-10-25T12:00:00Z").startOfTomorrow())
        .isEqualTo(Instant.parse("2026-10-25T22:00:00Z"));
  }

  @Test
  void dueMeansBeforeTheStartOfTomorrow() {
    ReviewDays days = at("2026-01-14T12:00:00Z");
    assertThat(days.isDue(Instant.parse("2026-01-14T21:59:59Z"))).isTrue();
    assertThat(days.isDue(Instant.parse("2026-01-10T22:00:00Z"))).isTrue();
    assertThat(days.isDue(days.startOfTomorrow())).isFalse();
    assertThat(days.isDue(null)).isFalse();
  }
}
