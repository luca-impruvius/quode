package com.quode.taxonomy.internal;

import com.quode.taxonomy.TopicLookup;
import com.quode.taxonomy.TopicRef;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    List<UUID> ids = topics.findSubtreeIds(ownerId, topicId);
    return ids.isEmpty() ? Optional.empty() : Optional.of(new HashSet<>(ids));
  }
}
