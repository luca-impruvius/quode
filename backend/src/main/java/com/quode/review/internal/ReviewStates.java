package com.quode.review.internal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads and writes review_state (V7). Every method is per owner. */
@Repository
class ReviewStates {

  private static final String COLUMNS =
      "owner_id, question_id, last_reviewed_at, due_at, interval_days, ease, reps,"
          + " lapses, learn_next";

  private final JdbcTemplate jdbc;

  ReviewStates(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Enrols a question as new; a question already enrolled keeps its state. */
  void enrol(UUID ownerId, UUID questionId, boolean learnNext) {
    jdbc.update(
        "insert into review_state (owner_id, question_id, learn_next) values (?, ?, ?)"
            + " on conflict (owner_id, question_id) do nothing",
        ownerId,
        questionId,
        learnNext);
  }

  /** {@code lock}: until commit, so two answers to one question are applied one after the other. */
  Optional<ReviewState> find(UUID ownerId, UUID questionId, boolean lock) {
    return jdbc
        .query(
            "select "
                + COLUMNS
                + " from review_state where owner_id = ? and question_id = ?"
                + (lock ? " for update" : ""),
            (rs, n) -> state(rs),
            ownerId,
            questionId)
        .stream()
        .findFirst();
  }

  /** A review answer: the schedule moves, and the answer counts toward today's limits. */
  void reviewed(ReviewState state, Schedule next, Instant answeredAt, Instant dueAt) {
    jdbc.update(
        "update review_state set interval_days = ?, ease = ?, reps = ?, lapses = ?, due_at = ?,"
            + " last_reviewed_at = ?, introduced_at = coalesce(introduced_at, ?)"
            + " where owner_id = ? and question_id = ?",
        next.intervalDays(),
        BigDecimal.valueOf(next.ease()),
        next.reps(),
        next.lapses(),
        Timestamp.from(dueAt),
        Timestamp.from(answeredAt),
        Timestamp.from(answeredAt),
        state.ownerId(),
        state.questionId());
  }

  /** A quiz answer (ADR-0012): only the schedule moves, never the review dates. */
  void rescheduled(ReviewState state, Schedule next, Instant dueAt) {
    jdbc.update(
        "update review_state set interval_days = ?, ease = ?, reps = ?, lapses = ?, due_at = ?"
            + " where owner_id = ? and question_id = ?",
        next.intervalDays(),
        BigDecimal.valueOf(next.ease()),
        next.reps(),
        next.lapses(),
        Timestamp.from(dueAt),
        state.ownerId(),
        state.questionId());
  }

  void setLearnNext(UUID ownerId, UUID questionId, boolean learnNext) {
    jdbc.update(
        "update review_state set learn_next = ? where owner_id = ? and question_id = ?",
        learnNext,
        ownerId,
        questionId);
  }

  /** Remove from review: the schedule and its history go too (the spec's trade-off). */
  void remove(UUID ownerId, UUID questionId) {
    jdbc.update(
        "delete from review_state where owner_id = ? and question_id = ?", ownerId, questionId);
  }

  /** Scheduled questions, soonest due first (so the most overdue come first). */
  List<Scheduled> scheduled(UUID ownerId) {
    return jdbc.query(
        "select question_id, due_at from review_state where owner_id = ? and due_at is not null"
            + " order by due_at, enrolled_at, question_id",
        (rs, n) ->
            new Scheduled(
                rs.getObject("question_id", UUID.class), rs.getTimestamp("due_at").toInstant()),
        ownerId);
  }

  /** New questions: "Learn next" ones first, then the oldest enrolled. */
  List<UUID> newOnes(UUID ownerId) {
    return jdbc.queryForList(
        "select question_id from review_state where owner_id = ? and last_reviewed_at is null"
            + " order by learn_next desc, enrolled_at, question_id",
        UUID.class,
        ownerId);
  }

  /** Today's counts: introductions, and reviews of questions introduced before today. */
  Counts countsSince(UUID ownerId, Instant startOfToday) {
    Timestamp since = Timestamp.from(startOfToday);
    return jdbc.queryForObject(
        "select count(*) filter (where introduced_at >= ?) as new_today,"
            + " count(*) filter (where last_reviewed_at >= ? and introduced_at < ?) as done_today"
            + " from review_state where owner_id = ?",
        (rs, n) -> new Counts(rs.getInt("new_today"), rs.getInt("done_today")),
        since,
        since,
        since,
        ownerId);
  }

  record Scheduled(UUID questionId, Instant dueAt) {}

  record Counts(int newToday, int doneToday) {}

  private static ReviewState state(ResultSet rs) throws SQLException {
    return new ReviewState(
        rs.getObject("owner_id", UUID.class),
        rs.getObject("question_id", UUID.class),
        instant(rs.getTimestamp("last_reviewed_at")),
        instant(rs.getTimestamp("due_at")),
        new Schedule(
            rs.getInt("interval_days"),
            rs.getBigDecimal("ease").doubleValue(),
            rs.getInt("reps"),
            rs.getInt("lapses")),
        rs.getBoolean("learn_next"));
  }

  private static Instant instant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
