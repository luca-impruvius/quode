package com.quode.taxonomy;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read access to topics for other modules (questions show and validate their topics). */
public interface TopicLookup {

  /**
   * The owner's topics among {@code ids}. Ids that don't exist or belong to someone else are simply
   * missing from the result, so callers can't tell the two apart.
   */
  Map<UUID, TopicRef> findOwned(UUID ownerId, Collection<UUID> ids);

  /**
   * The topic and all its descendants, for "this topic and its sub-topics" filters. Empty when the
   * topic doesn't exist or belongs to someone else.
   */
  Optional<Set<UUID>> subtreeIds(UUID ownerId, UUID topicId);
}
