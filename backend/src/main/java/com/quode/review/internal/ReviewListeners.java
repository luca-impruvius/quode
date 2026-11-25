package com.quode.review.internal;

import com.quode.question.QuestionCreated;
import com.quode.study.AttemptRecorded;
import com.quode.user.StudyLimits;
import java.time.Instant;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Enrolment and scheduling from other modules' events. Synchronous, in the publisher's transaction:
 * the schedule is exact at once, and a failure rolls the answer back instead of losing the update
 * (docs/specs/M3-daily-review.md, Module boundaries).
 */
@Component
class ReviewListeners {

  private final ReviewStates states;
  private final SchedulingStrategy scheduler;
  private final StudyLimits limits;

  ReviewListeners(ReviewStates states, SchedulingStrategy scheduler, StudyLimits limits) {
    this.states = states;
    this.scheduler = scheduler;
    this.limits = limits;
  }

  @EventListener
  void on(QuestionCreated event) {
    states.enrol(event.ownerId(), event.questionId(), false);
  }

  /**
   * A review answer always moves the schedule. A quiz answer (ADR-0012) moves a scheduled
   * question's schedule on Missed or Partial, or on Got it when it is due, and leaves a new
   * question new. No state, no change: a quiz never enrols, and a removed question stays removed.
   */
  @EventListener
  void on(AttemptRecorded event) {
    ReviewState state = states.find(event.ownerId(), event.questionId(), true).orElse(null);
    if (state == null) {
      return;
    }
    Instant answeredAt = event.answeredAt();
    ReviewDays days = ReviewDays.of(answeredAt, limits.of(event.ownerId()).timeZone());
    Grade grade = Grade.ofOutcome(event.outcome());
    boolean review = "REVIEW".equals(event.sessionKind());
    if (!review && (state.isNew() || (grade == Grade.GOT_IT && !days.isDue(state.dueAt())))) {
      return;
    }
    Schedule next = scheduler.next(state.schedule(), grade);
    Instant dueAt = days.dueAt(next.intervalDays());
    if (review) {
      states.reviewed(state, next, answeredAt, dueAt);
    } else {
      states.rescheduled(state, next, dueAt);
    }
  }
}
