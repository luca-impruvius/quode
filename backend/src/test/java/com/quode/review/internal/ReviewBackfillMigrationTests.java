package com.quode.review.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V7 enrols every question that existed before M3 as new, with {@code enrolled_at} its creation
 * time, and gives existing users the default limits. Migrates a fresh database to V6, adds data,
 * then runs the rest.
 */
class ReviewBackfillMigrationTests {

  private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  @BeforeAll
  static void migrateWithExistingQuestions() throws SQLException {
    postgres.start();
    flyway("6").migrate();
    try (Connection connection = connect();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into app_user (id, email, google_sub, display_name)
            values ('00000000-0000-7000-8000-000000000001', 'me@example.com', 'sub-me', 'U');
          insert into topic (id, owner_id, name)
            values ('00000000-0000-7000-8000-000000000002',
                    '00000000-0000-7000-8000-000000000001', 'Cloud');
          insert into question (id, owner_id, type, prompt_md, answer_md, status, primary_topic_id,
                                archived_at, created_at)
            values ('00000000-0000-7000-8000-000000000003', '00000000-0000-7000-8000-000000000001',
                    'FLASHCARD', 'Active?', 'x', 'ACTIVE', '00000000-0000-7000-8000-000000000002',
                    null, '2026-01-02T03:04:05Z'),
                   ('00000000-0000-7000-8000-000000000004', '00000000-0000-7000-8000-000000000001',
                    'FLASHCARD', 'Draft?', null, 'DRAFT', '00000000-0000-7000-8000-000000000002',
                    null, '2026-01-03T00:00:00Z'),
                   ('00000000-0000-7000-8000-000000000005', '00000000-0000-7000-8000-000000000001',
                    'FLASHCARD', 'Archived?', 'x', 'ACTIVE', '00000000-0000-7000-8000-000000000002',
                    now(), '2026-01-04T00:00:00Z');
          insert into question_topic (question_id, topic_id, owner_id)
            select id, primary_topic_id, owner_id from question;
          """);
    }
    flyway(null).migrate();
  }

  private static Flyway flyway(String target) {
    var config =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    return (target == null ? config : config.target(target)).load();
  }

  private static Connection connect() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  @AfterAll
  static void stop() {
    postgres.stop();
  }

  @Test
  void everyExistingQuestionIsEnrolledAsNewSinceItWasCreated() throws SQLException {
    try (Connection connection = connect();
        Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "select q.prompt_md, rs.enrolled_at = q.created_at as since_created,"
                    + " rs.due_at is null and rs.introduced_at is null"
                    + " and rs.last_reviewed_at is null and rs.reps = 0 and rs.lapses = 0"
                    + " and rs.interval_days = 0 and rs.ease = 2.5 and not rs.learn_next as is_new"
                    + " from question q join review_state rs"
                    + " on rs.question_id = q.id and rs.owner_id = q.owner_id order by q.created_at")) {
      int count = 0;
      while (rows.next()) {
        count++;
        assertThat(rows.getBoolean("since_created")).as(rows.getString(1)).isTrue();
        assertThat(rows.getBoolean("is_new")).as(rows.getString(1)).isTrue();
      }
      assertThat(count).isEqualTo(3);
    }
  }

  @Test
  void existingUsersGetTheDefaultLimits() throws SQLException {
    try (Connection connection = connect();
        Statement statement = connection.createStatement();
        ResultSet row =
            statement.executeQuery("select new_per_day, reviews_per_day from app_user")) {
      row.next();
      assertThat(row.getInt(1)).isEqualTo(5);
      assertThat(row.getInt(2)).isEqualTo(30);
    }
  }
}
