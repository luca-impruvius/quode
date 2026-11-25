package com.quode.study.internal;

import com.quode.study.StudySessions;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kept apart from StudyService, which needs the review queue: no circular dependency. */
@Service
class StudySessionsQuery implements StudySessions {

  private final JdbcTemplate jdbc;

  StudySessionsQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> unfinishedReview(UUID ownerId, Instant since) {
    return jdbc
        .queryForList(
            "select id from study_session where owner_id = ? and kind = 'REVIEW'"
                + " and finished_at is null and current_index < cardinality(question_order)"
                + " and started_at >= ? order by started_at desc, id desc limit 1",
            UUID.class,
            ownerId,
            Timestamp.from(since))
        .stream()
        .findFirst();
  }
}
