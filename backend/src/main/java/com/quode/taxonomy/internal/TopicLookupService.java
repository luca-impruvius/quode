package com.quode.taxonomy.internal;

import com.quode.taxonomy.TopicLookup;
import com.quode.taxonomy.TopicRef;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TopicLookupService implements TopicLookup {

  private final TopicRepository topics;

  TopicLookupService(TopicRepository topics) {
    this.topics = topics;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, TopicRef> findOwned(UUID ownerId, Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<UUID, TopicRef> found = new HashMap<>();
    for (TopicRepository.PathRow row : topics.findPaths(ownerId, ids.toArray(UUID[]::new))) {
      found.put(row.getId(), new TopicRef(row.getId(), row.getName(), row.getPath()));
    }
    return found;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Set<UUID>> subtreeIds(UUID ownerId, UUID topicId) {
    if (topics.findByIdAndOwnerId(topicId, ownerId).isEmpty()) {
      return Optional.empty();
    }
    Set<UUID> ids = new HashSet<>();
    for (TopicRepository.SubtreeRow row : topics.findSubtrees(ownerId)) {
      if (row.getTopicId().equals(topicId)) {
        ids.add(row.getMemberId());
      }
    }
    return Optional.of(ids);
  }
}
