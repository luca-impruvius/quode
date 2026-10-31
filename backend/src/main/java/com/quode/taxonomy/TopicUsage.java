package com.quode.taxonomy;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * How other modules tell taxonomy which questions use a topic, without taxonomy depending on them:
 * the question module implements this (M1-04). With no implementation, no topic is used.
 */
public interface TopicUsage {

  /**
   * Non-archived question ids per topic of this owner, for the counts. Topics without any: absent.
   */
  Map<UUID, Set<UUID>> activeQuestionIdsByTopic(UUID ownerId);

  /** Whether any question, archived ones included, still links to this topic. */
  boolean isUsed(UUID topicId);
}
