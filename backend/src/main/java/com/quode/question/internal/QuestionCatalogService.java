package com.quode.question.internal;

import com.quode.question.QuestionCatalog;
import com.quode.taxonomy.TopicLookup;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class QuestionCatalogService implements QuestionCatalog {

  private static final String STUDYABLE = "q.status = 'ACTIVE' and q.archived_at is null";

  private final NamedParameterJdbcTemplate jdbc;
  private final QuestionRepository questions;
  private final TopicLookup topics;

  QuestionCatalogService(
      NamedParameterJdbcTemplate jdbc, QuestionRepository questions, TopicLookup topics) {
    this.jdbc = jdbc;
    this.questions = questions;
    this.topics = topics;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> studyableIds(UUID ownerId, Collection<UUID> topicIds) {
    Set<UUID> subtree = new HashSet<>();
    topicIds.forEach(t -> topics.subtreeIds(ownerId, t).ifPresent(subtree::addAll));
    if (subtree.isEmpty()) {
      return List.of();
    }
    return jdbc.queryForList(
        "select q.id from question q where q.owner_id = :owner and "
            + STUDYABLE
            + " and exists (select 1 from question_topic qt"
            + " where qt.question_id = q.id and qt.topic_id in (:topics))"
            + " order by q.created_at, q.id",
        new MapSqlParameterSource("owner", ownerId).addValue("topics", subtree),
        UUID.class);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> keepStudyable(UUID ownerId, List<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Set<UUID> ok =
        new HashSet<>(
            jdbc.queryForList(
                "select q.id from question q where q.owner_id = :owner and q.id in (:ids) and "
                    + STUDYABLE,
                new MapSqlParameterSource("owner", ownerId).addValue("ids", ids),
                UUID.class));
    return ids.stream().filter(ok::contains).distinct().toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, StudyQuestion> find(UUID ownerId, Collection<UUID> ids) {
    Map<UUID, StudyQuestion> found = new HashMap<>();
    if (ids.isEmpty()) {
      return found;
    }
    for (Question q : questions.findAllById(ids)) {
      if (q.getOwnerId().equals(ownerId)) {
        found.put(q.getId(), view(q));
      }
    }
    return found;
  }

  private static StudyQuestion view(Question q) {
    return new StudyQuestion(
        q.getId(),
        Type.valueOf(q.getType().name()),
        q.getPromptMd(),
        q.getAnswerMd(),
        q.getExplanationMd(),
        q.getOptions().stream()
            .map(o -> new StudyQuestion.Option(o.getId(), o.getTextMd(), o.isCorrect()))
            .toList());
  }
}
