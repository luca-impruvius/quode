package com.quode.question.internal;

import java.util.UUID;

/**
 * One Library request, already checked (docs/specs/M1-library.md). {@code text} null means no
 * search; {@code topicId} null means all topics.
 */
record LibraryQuery(
    UUID topicId,
    QuestionType type,
    Integer difficulty,
    Status status,
    String text,
    Sort sort,
    int page,
    int size) {

  /** ACTIVE: ready to study. NEEDS_ANSWER: drafts without their answer yet (M2). */
  enum Status {
    ACTIVE,
    NEEDS_ANSWER,
    ARCHIVED,
    FLAGGED
  }

  /** Newest first, most recently edited first, or easiest first (then newest). */
  enum Sort {
    CREATED("q.created_at desc"),
    UPDATED("q.updated_at desc"),
    DIFFICULTY("q.difficulty asc, q.created_at desc");

    /** A fixed ORDER BY fragment: never built from request text. */
    final String orderBy;

    Sort(String orderBy) {
      this.orderBy = orderBy;
    }
  }
}
