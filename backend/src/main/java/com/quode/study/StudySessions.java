package com.quode.study;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Read access to study sessions for other modules. Every method is per owner. */
public interface StudySessions {

  /**
   * The owner's latest REVIEW session started at or after {@code since} with questions still to
   * answer, if any.
   */
  Optional<UUID> unfinishedReview(UUID ownerId, Instant since);
}
