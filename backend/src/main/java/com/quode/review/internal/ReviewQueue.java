package com.quode.review.internal;

import com.quode.question.QuestionCatalog;
import com.quode.review.internal.ReviewStates.Counts;
import com.quode.review.internal.ReviewStates.Scheduled;
import com.quode.study.ReviewQueueSource;
import com.quode.study.StudySessions;
import com.quode.user.StudyLimits;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Today's queue (docs/specs/M3-daily-review.md, Today's queue): due reviews most overdue first up
 * to what's left of the review limit, then new ones up to what's left of the new limit, paused
 * while the backlog is over twice the review limit. Drafts and archived questions are left out
 * through the question module, never by joining its tables.
 */
@Service
class ReviewQueue implements ReviewQueueSource {

  static final int LEARN_MORE = 5;

  private final ReviewStates states;
  private final QuestionCatalog catalog;
  private final StudyLimits limits;
  private final StudySessions sessions;
  private final Clock clock;

  ReviewQueue(
      ReviewStates states,
      QuestionCatalog catalog,
      StudyLimits limits,
      StudySessions sessions,
      Clock clock) {
    this.states = states;
    this.catalog = catalog;
    this.limits = limits;
    this.sessions = sessions;
    this.clock = clock;
  }

  /** What Today shows. */
  record Today(
      int due,
      int newWaiting,
      int moreDue,
      int moreNew,
      int doneToday,
      int newToday,
      Instant nextDueAt,
      UUID sessionId) {}

  // Everything waiting today, and what today's limits allow of it.
  private record Plan(
      ReviewDays days,
      int reviewsPerDay,
      List<UUID> due,
      List<UUID> fresh,
      int dueAllowed,
      int newAllowed,
      Instant nextDueAt,
      Counts counts) {}

  @Override
  @Transactional(readOnly = true)
  public Instant startOfToday(UUID ownerId) {
    return ReviewDays.of(clock.instant(), limits.of(ownerId).timeZone()).startOfToday();
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> next(UUID ownerId, Extra extra) {
    Plan plan = plan(ownerId);
    return switch (extra) {
      case NONE ->
          mix(plan.due().subList(0, plan.dueAllowed()), plan.fresh().subList(0, plan.newAllowed()));
      case KEEP_GOING -> List.copyOf(first(plan.due(), plan.reviewsPerDay()));
      case LEARN_MORE -> List.copyOf(first(plan.fresh(), LEARN_MORE));
    };
  }

  @Transactional(readOnly = true)
  Today today(UUID ownerId) {
    Plan plan = plan(ownerId);
    return new Today(
        plan.dueAllowed(),
        plan.newAllowed(),
        plan.due().size() - plan.dueAllowed(),
        plan.fresh().size() - plan.newAllowed(),
        plan.counts().doneToday(),
        plan.counts().newToday(),
        plan.nextDueAt(),
        sessions.unfinishedReview(ownerId, plan.days().startOfToday()).orElse(null));
  }

  private Plan plan(UUID ownerId) {
    StudyLimits.Limits limit = limits.of(ownerId);
    ReviewDays days = ReviewDays.of(clock.instant(), limit.timeZone());
    Counts counts = states.countsSince(ownerId, days.startOfToday());

    List<Scheduled> scheduled = states.scheduled(ownerId);
    Set<UUID> studyable =
        new HashSet<>(
            catalog.keepStudyable(ownerId, scheduled.stream().map(Scheduled::questionId).toList()));
    List<UUID> due = new ArrayList<>();
    Instant nextDueAt = null;
    for (Scheduled s : scheduled) {
      if (!studyable.contains(s.questionId())) {
        continue;
      }
      if (days.isDue(s.dueAt())) {
        due.add(s.questionId());
      } else if (nextDueAt == null) {
        nextDueAt = s.dueAt();
      }
    }
    List<UUID> fresh = catalog.keepStudyable(ownerId, states.newOnes(ownerId));

    int reviewsLeft = Math.max(0, limit.reviewsPerDay() - counts.doneToday());
    boolean backlog = due.size() > 2 * limit.reviewsPerDay();
    int newLeft = backlog ? 0 : Math.max(0, limit.newPerDay() - counts.newToday());
    return new Plan(
        days,
        limit.reviewsPerDay(),
        due,
        fresh,
        Math.min(due.size(), reviewsLeft),
        Math.min(fresh.size(), newLeft),
        nextDueAt,
        counts);
  }

  private static List<UUID> first(List<UUID> ids, int count) {
    return ids.subList(0, Math.min(count, ids.size()));
  }

  /** Spreads the new questions evenly through the due ones, keeping both orders. */
  static List<UUID> mix(List<UUID> due, List<UUID> fresh) {
    List<UUID> mixed = new ArrayList<>(due);
    int n = fresh.size();
    for (int j = 0; j < n; j++) {
      int position = (int) ((long) (j + 1) * due.size() / (n + 1)) + j;
      mixed.add(position, fresh.get(j));
    }
    return List.copyOf(mixed);
  }
}
