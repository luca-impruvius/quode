package com.quode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Stops this context's database, so the context is thrown away afterwards (@DirtiesContext).
// A short connection timeout makes the health check fail fast instead of waiting 30 s for Hikari.
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {"management.server.port=0", "spring.datasource.hikari.connection-timeout=1000"})
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class HealthWhenDatabaseDownTests {

  @LocalServerPort private int serverPort;

  @Autowired private PostgreSQLContainer postgres;

  @Test
  void publicHealthIsDownWithoutDetailsWhenTheDatabaseIsUnreachable() {
    postgres.stop();

    RestTestClient.bindToServer()
        .baseUrl("http://127.0.0.1:" + serverPort)
        .build()
        .get()
        .uri("/api/health")
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .json("{\"status\":\"DOWN\"}", JsonCompareMode.STRICT);
  }
}
