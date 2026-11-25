package com.quode.study;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Today's review queue, for starting a REVIEW session. Declared here and implemented by the review
 * module, so dependencies run one way: review → study (docs/specs/M3-daily-review.md).
 */
public interface ReviewQueueSource {

  /** The start of the owner's current day (their time zone), as an instant. */
  Instant startOfToday(UUID ownerId);

  /** The questions for a new review session, in order; empty when there is nothing to review. */
  List<UUID> next(UUID ownerId, Extra extra);

  /** NONE: today's queue. KEEP_GOING: due reviews past the limit. LEARN_MORE: 5 more new ones. */
  enum Extra {
    NONE,
    KEEP_GOING,
    LEARN_MORE
  }
}
