package com.quode.review.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** SM-2 adapted to three grades (docs/spaced-repetition.md). Pure: no Spring, no database. */
class Sm2SchedulerTests {

  private final SchedulingStrategy scheduler = new Sm2Scheduler();

  private Schedule after(Schedule start, Grade... grades) {
    Schedule s = start;
    for (Grade g : grades) {
      s = scheduler.next(s, g);
    }
    return s;
  }

  @Test
  void aNewQuestionStartsAtTheDefaults() {
    assertThat(Schedule.NEW).isEqualTo(new Schedule(0, 2.5, 0, 0));
  }

  @Test
  void theFirstGotItIsOneDayAndTheSecondThreeDays() {
    Schedule first = after(Schedule.NEW, Grade.GOT_IT);
    assertThat(first).isEqualTo(new Schedule(1, 2.55, 1, 0));
    Schedule second = after(first, Grade.GOT_IT);
    assertThat(second).isEqualTo(new Schedule(3, 2.6, 2, 0));
  }

  @Test
  void laterGotItsMultiplyByTheNewEase() {
    // 3 days at ease 2.6: the ease rises to 2.65 first, then 3 × 2.65 = 7.95 → 8 days.
    assertThat(after(new Schedule(3, 2.6, 2, 0), Grade.GOT_IT))
        .isEqualTo(new Schedule(8, 2.65, 3, 0));
  }

  @Test
  void intervalsRoundToWholeDays() {
    // 10 × 1.35 = 13.5 → 14 (half up); 7 × 1.2 = 8.4 → 8 for Partial.
    assertThat(after(new Schedule(10, 1.3, 5, 2), Grade.GOT_IT).intervalDays()).isEqualTo(14);
    assertThat(after(new Schedule(7, 2.0, 5, 0), Grade.PARTIAL).intervalDays()).isEqualTo(8);
  }

  @Test
  void missedResetsRepsCountsALapseAndComesBackTomorrow() {
    assertThat(after(new Schedule(20, 2.5, 6, 1), Grade.MISSED))
        .isEqualTo(new Schedule(1, 2.3, 0, 2));
  }

  @Test
  void aGotItAfterMissedIsAFirstGotItAgain() {
    assertThat(after(new Schedule(20, 2.5, 6, 1), Grade.MISSED, Grade.GOT_IT).intervalDays())
        .isEqualTo(1);
  }

  @Test
  void partialKeepsRepsAndStretchesTheIntervalAtLeastToOneDay() {
    assertThat(after(new Schedule(10, 2.5, 4, 0), Grade.PARTIAL))
        .isEqualTo(new Schedule(12, 2.35, 4, 0));
    assertThat(after(Schedule.NEW, Grade.PARTIAL)).isEqualTo(new Schedule(1, 2.35, 0, 0));
  }

  @Test
  void theEaseStaysBetween1point3And3() {
    assertThat(after(new Schedule(1, 1.4, 0, 0), Grade.MISSED).ease()).isEqualTo(1.3);
    assertThat(after(new Schedule(1, 1.35, 0, 0), Grade.PARTIAL).ease()).isEqualTo(1.3);
    assertThat(after(new Schedule(5, 2.98, 3, 0), Grade.GOT_IT).ease()).isEqualTo(3.0);
    // The clamped ease is the one the interval uses: 5 × 3.0 = 15.
    assertThat(after(new Schedule(5, 3.0, 3, 0), Grade.GOT_IT).intervalDays()).isEqualTo(15);
  }

  @Test
  void repeatedSmallStepsDoNotDriftFromTwoDecimals() {
    Schedule s = Schedule.NEW;
    for (int i = 0; i < 9; i++) {
      s = scheduler.next(s, Grade.PARTIAL);
    }
    assertThat(s.ease()).isEqualTo(1.3);
    s = after(new Schedule(1, 2.5, 3, 0), Grade.GOT_IT, Grade.GOT_IT, Grade.GOT_IT);
    assertThat(s.ease()).isEqualTo(2.65);
  }

  @Test
  void intervalsStopAtAHundredYears() {
    assertThat(after(new Schedule(30_000, 3.0, 9, 0), Grade.GOT_IT).intervalDays())
        .isEqualTo(Sm2Scheduler.MAX_INTERVAL);
  }

  @Test
  void gradesComeFromAttemptOutcomes() {
    assertThat(Grade.ofOutcome("CORRECT")).isEqualTo(Grade.GOT_IT);
    assertThat(Grade.ofOutcome("PARTIAL")).isEqualTo(Grade.PARTIAL);
    assertThat(Grade.ofOutcome("WRONG")).isEqualTo(Grade.MISSED);
  }
}
