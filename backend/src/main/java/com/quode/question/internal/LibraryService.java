package com.quode.question.internal;

import com.quode.shared.NotFoundException;
import com.quode.taxonomy.TopicLookup;
import com.quode.taxonomy.TopicRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Library list: filters, search and paging in one SQL query (plus a count). Every value is a
 * bound parameter; the ORDER BY comes from a fixed enum.
 */
@Service
class LibraryService {

  private final NamedParameterJdbcTemplate jdbc;
  private final TopicLookup topics;

  LibraryService(NamedParameterJdbcTemplate jdbc, TopicLookup topics) {
    this.jdbc = jdbc;
    this.topics = topics;
  }

  private record Row(
      UUID id,
      QuestionType type,
      String promptMd,
      int difficulty,
      UUID primaryTopicId,
      boolean archived,
      boolean flagged,
      String flagNote,
      Instant createdAt,
      Instant updatedAt) {}

  @Transactional(readOnly = true)
  LibraryPage list(UUID ownerId, LibraryQuery query) {
    MapSqlParameterSource params = new MapSqlParameterSource("owner", ownerId);
    StringBuilder where = new StringBuilder("q.owner_id = :owner");

    switch (query.status()) {
      case ACTIVE -> where.append(" and q.archived_at is null");
      case ARCHIVED -> where.append(" and q.archived_at is not null");
      case FLAGGED -> where.append(" and q.flagged_at is not null and q.archived_at is null");
    }
    if (query.type() != null) {
      where.append(" and q.type = :type");
      params.addValue("type", query.type().name());
    }
    if (query.difficulty() != null) {
      where.append(" and q.difficulty = :difficulty");
      params.addValue("difficulty", query.difficulty());
    }
    if (query.topicId() != null) {
      // Another user's topic looks like a missing one.
      Set<UUID> subtree =
          topics
              .subtreeIds(ownerId, query.topicId())
              .orElseThrow(() -> new NotFoundException(QuestionService.TOPIC_NOT_FOUND));
      where.append(
          " and exists (select 1 from question_topic qt"
              + " where qt.question_id = q.id and qt.topic_id in (:topicIds))");
      params.addValue("topicIds", subtree);
    }
    if (query.text() != null) {
      where.append(
          " and (q.prompt_md ilike :pattern escape '\\' or q.answer_md ilike :pattern escape '\\')");
      params.addValue("pattern", "%" + escapeLike(query.text()) + "%");
    }

    Long total =
        jdbc.queryForObject("select count(*) from question q where " + where, params, Long.class);
    params.addValue("limit", query.size());
    params.addValue("offset", (long) query.page() * query.size());
    List<Row> rows =
        jdbc.query(
            """
            select q.id, q.type, q.prompt_md, q.difficulty, q.primary_topic_id,
                   q.archived_at is not null as archived, q.flagged_at is not null as flagged, q.flag_note,
                   q.created_at, q.updated_at
              from question q
             where %s
             order by %s, q.id desc
             limit :limit offset :offset
            """
                .formatted(where, query.sort().orderBy),
            params,
            LibraryService::row);

    Map<UUID, TopicRef> refs =
        topics.findOwned(ownerId, rows.stream().map(Row::primaryTopicId).distinct().toList());
    List<LibraryItem> items = new ArrayList<>(rows.size());
    for (Row r : rows) {
      items.add(
          new LibraryItem(
              r.id(),
              r.type(),
              r.promptMd(),
              r.difficulty(),
              refs.get(r.primaryTopicId()),
              r.archived(),
              r.flagged(),
              r.flagNote(),
              r.createdAt(),
              r.updatedAt()));
    }
    long totalElements = total == null ? 0 : total;
    boolean hasNext = (query.page() + 1L) * query.size() < totalElements;
    return new LibraryPage(items, query.page(), query.size(), totalElements, hasNext);
  }

  // The search text matches literally: % and _ (and the escape character) lose their LIKE meaning.
  static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static Row row(ResultSet rs, int rowNum) throws SQLException {
    return new Row(
        rs.getObject("id", UUID.class),
        QuestionType.valueOf(rs.getString("type")),
        rs.getString("prompt_md"),
        rs.getInt("difficulty"),
        rs.getObject("primary_topic_id", UUID.class),
        rs.getBoolean("archived"),
        rs.getBoolean("flagged"),
        rs.getString("flag_note"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
