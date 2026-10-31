package com.quode.question.internal;

import com.quode.taxonomy.TopicUsage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Tells taxonomy which questions use its topics, so taxonomy never depends on this module. */
@Component
class QuestionTopicUsage implements TopicUsage {

  private final JdbcTemplate jdbc;

  QuestionTopicUsage(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Map<UUID, Set<UUID>> activeQuestionIdsByTopic(UUID ownerId) {
    Map<UUID, Set<UUID>> byTopic = new HashMap<>();
    jdbc.query(
        """
        select qt.topic_id, qt.question_id
          from question_topic qt join question q on q.id = qt.question_id
         where q.owner_id = ? and q.archived_at is null
        """,
        rs -> {
          byTopic
              .computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashSet<>())
              .add(rs.getObject(2, UUID.class));
        },
        ownerId);
    return byTopic;
  }

  @Override
  public boolean isUsed(UUID topicId) {
    Boolean used =
        jdbc.queryForObject(
            "select exists (select 1 from question_topic where topic_id = ?)",
            Boolean.class,
            topicId);
    return Boolean.TRUE.equals(used);
  }
}
