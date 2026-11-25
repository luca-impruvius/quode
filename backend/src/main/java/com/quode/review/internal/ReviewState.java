package com.quode.review.internal;

import java.time.Instant;
import java.util.UUID;

/** One row of review_state: a user's schedule for one question. */
record ReviewState(
    UUID ownerId,
    UUID questionId,
    Instant lastReviewedAt,
    Instant dueAt,
    Schedule schedule,
    boolean learnNext) {

  /** Enrolled but never answered in a review. */
  boolean isNew() {
    return lastReviewedAt == null;
  }
}
