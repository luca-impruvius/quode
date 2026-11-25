package com.quode.review.internal;

import org.springframework.stereotype.Component;

/**
 * SM-2 adapted to three grades (docs/spaced-repetition.md). The ease changes first and the interval
 * uses the new, clamped ease. Works in hundredths of ease, so whole-day rounding is exact half-up.
 */
@Component
class Sm2Scheduler implements SchedulingStrategy {

  private static final int MIN_EASE = 130;
  private static final int MAX_EASE = 300;

  @Override
  public Schedule next(Schedule current, Grade grade) {
    int ease = (int) Math.round(current.ease() * 100);
    int interval = current.intervalDays();
    return switch (grade) {
      case MISSED -> new Schedule(1, clamp(ease - 20) / 100.0, 0, current.lapses() + 1);
      case PARTIAL -> {
        int newEase = clamp(ease - 15);
        yield new Schedule(
            Math.max(1, times(interval, 120)), newEase / 100.0, current.reps(), current.lapses());
      }
      case GOT_IT -> {
        int newEase = clamp(ease + 5);
        int reps = current.reps() + 1;
        int days =
            switch (reps) {
              case 1 -> 1;
              case 2 -> 3;
              default -> Math.max(1, times(interval, newEase));
            };
        yield new Schedule(days, newEase / 100.0, reps, current.lapses());
      }
    };
  }

  // interval × hundredths / 100, rounded half up.
  private static int times(int interval, int hundredths) {
    return (int) ((interval * (long) hundredths + 50) / 100);
  }

  private static int clamp(int ease) {
    return Math.clamp(ease, MIN_EASE, MAX_EASE);
  }
}
