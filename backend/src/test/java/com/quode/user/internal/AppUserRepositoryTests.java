package com.quode.user.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quode.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// Runs against the real schema from the Flyway migrations; each test rolls back.
@SpringBootTest(
    properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
            + "com.quode.user.internal.RecordingStatementInspector")
@Import(TestcontainersConfiguration.class)
@Transactional
class AppUserRepositoryTests {

  @Autowired private AppUserRepository repository;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private EntityManager entityManager;

  @Test
  void migrationV1IsApplied() {
    Boolean success =
        jdbc.queryForObject(
            "select success from flyway_schema_history where version = '1'", Boolean.class);

    assertThat(success).isTrue();
  }

  @Test
  void savesAndFindsByEmailFromTheDatabase() {
    UUID id = repository.saveAndFlush(new AppUser("me@example.com", "sub-1", "Me")).getId();
    // Forget the managed instance, so the lookup below really reads and maps the stored row.
    entityManager.clear();

    AppUser found = repository.findByEmail("me@example.com").orElseThrow();
    assertThat(found.getId()).isEqualTo(id);
    assertThat(found.getEmail()).isEqualTo("me@example.com");
    assertThat(found.getGoogleSub()).isEqualTo("sub-1");
    assertThat(found.getDisplayName()).isEqualTo("Me");
    assertThat(found.getTimeZone()).isEqualTo("Europe/Bucharest");
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getUpdatedAt()).isEqualTo(found.getCreatedAt());
  }

  @Test
  void storesEmailsLowercasedAndTrimmed() {
    UUID id = repository.saveAndFlush(new AppUser("  Me@Example.COM ", "sub-1", "Me")).getId();

    String stored =
        jdbc.queryForObject("select email from app_user where id = ?", String.class, id);
    assertThat(stored).isEqualTo("me@example.com");
  }

  @Test
  void findsByEmailIgnoringCaseAndSurroundingSpaces() {
    repository.saveAndFlush(new AppUser("me@example.com", "sub-1", "Me"));
    entityManager.clear();

    assertThat(repository.findByEmail("ME@Example.com")).isPresent();
    assertThat(repository.findByEmail(" me@example.com ")).isPresent();
    assertThat(repository.findByEmail("other@example.com")).isEmpty();
  }

  @Test
  void rejectsTheSameEmailInADifferentCase() {
    // Plain SQL, so the entity's lowercasing can't hide a missing index.
    insertRaw("me@example.com", "sub-1");

    assertThatThrownBy(() -> insertRaw("ME@example.com", "sub-2"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsADuplicateGoogleSub() {
    insertRaw("me@example.com", "sub-1");

    assertThatThrownBy(() -> insertRaw("other@example.com", "sub-1"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void entityGeneratesUuidV7Ids() {
    AppUser saved = repository.saveAndFlush(new AppUser("me@example.com", "sub-1", "Me"));

    assertThat(saved.getId().version()).isEqualTo(7);
  }

  @Test
  void databaseDefaultGeneratesUuidV7Ids() {
    UUID id =
        jdbc.queryForObject(
            "insert into app_user (email, google_sub, display_name)"
                + " values ('me@example.com', 'sub-1', 'Me') returning id",
            UUID.class);

    assertThat(id.version()).isEqualTo(7);
  }

  @Test
  void emailLookupSqlUsesTheLowerEmailIndex() {
    RecordingStatementInspector.STATEMENTS.clear();
    repository.findByEmail("me@example.com");
    String sql =
        RecordingStatementInspector.STATEMENTS.stream()
            .filter(s -> s.contains("from app_user"))
            .findFirst()
            .orElseThrow();
    assertThat(sql).contains("lower(").doesNotContain("upper(");

    // EXPLAIN the exact statement Hibernate sent, prepared with its parameter as $1. With
    // sequential scans off, the planner picks the index when the predicate matches it.
    jdbc.execute("set local enable_seqscan = off");
    jdbc.execute("prepare find_by_email(varchar) as " + sql.replace("?", "$1"));
    String plan =
        String.join(
            "\n",
            jdbc.queryForList("explain execute find_by_email('me@example.com')", String.class));
    jdbc.execute("deallocate find_by_email");

    assertThat(plan).contains("app_user_email_lower_key");
  }

  private void insertRaw(String email, String googleSub) {
    jdbc.update(
        "insert into app_user (email, google_sub, display_name) values (?, ?, 'Me')",
        email,
        googleSub);
  }
}
