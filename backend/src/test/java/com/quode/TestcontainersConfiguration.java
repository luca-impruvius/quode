package com.quode;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// The one Postgres for tests: every @SpringBootTest imports this. @ServiceConnection points the
// datasource at the container, so no URL or credentials are configured anywhere.
// Same image as compose.dev.yml and production.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:18");
  }
}
