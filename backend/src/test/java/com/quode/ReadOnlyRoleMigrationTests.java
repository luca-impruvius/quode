package com.quode;

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
 * The production read-only role (deploy/initdb/10-readonly-role.sh) can read the app's tables but
 * not the sessions (V5). Runs the init script's statements, then the real migrations, on a fresh
 * database of its own.
 */
class ReadOnlyRoleMigrationTests {

  private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  @BeforeAll
  static void migrateWithTheRole() throws SQLException {
    postgres.start();
    try (Connection connection = owner();
        Statement statement = connection.createStatement()) {
      // The same statements as deploy/initdb/10-readonly-role.sh.
      statement.execute("CREATE ROLE quode_readonly LOGIN PASSWORD 'test'");
      statement.execute("GRANT USAGE ON SCHEMA public TO quode_readonly");
      statement.execute(
          "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO quode_readonly");
    }
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .load()
        .migrate();
  }

  @AfterAll
  static void stop() {
    postgres.stop();
  }

  @Test
  void theReadOnlyRoleReadsTheAppTables() throws SQLException {
    assertThat(canSelect("question")).isTrue();
    assertThat(canSelect("topic")).isTrue();
    assertThat(canSelect("app_user")).isTrue();
  }

  @Test
  void theReadOnlyRoleCannotReadSessions() throws SQLException {
    assertThat(canSelect("spring_session")).isFalse();
    assertThat(canSelect("spring_session_attributes")).isFalse();
  }

  @Test
  void theReadOnlyRoleCannotWrite() throws SQLException {
    try (Connection connection = owner();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "select has_table_privilege('quode_readonly', 'question', 'INSERT')")) {
      result.next();
      assertThat(result.getBoolean(1)).isFalse();
    }
  }

  private static boolean canSelect(String table) throws SQLException {
    try (Connection connection = owner();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "select has_table_privilege('quode_readonly', '" + table + "', 'SELECT')")) {
      result.next();
      return result.getBoolean(1);
    }
  }

  private static Connection owner() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
