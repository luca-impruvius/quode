package com.quode.taxonomy.internal;

import com.quode.taxonomy.TopicPaths;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class TopicPathService implements TopicPaths {

  private final TopicService topics;

  TopicPathService(TopicService topics) {
    this.topics = topics;
  }

  @Override
  public Resolved resolve(UUID ownerId, Collection<String> paths) {
    return topics.resolvePaths(ownerId, paths);
  }
}
