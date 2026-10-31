package com.quode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

// Starts the real app with both servers on random free ports, so the test never clashes with a
// running bootRun on 8080/8081. management.server.port=0 keeps it a separate server, as in prod.
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0")
@Import(TestcontainersConfiguration.class)
class HealthEndpointTests {

  @LocalServerPort private int serverPort;

  @LocalManagementPort private int managementPort;

  private RestTestClient server;
  private RestTestClient management;

  @BeforeEach
  void setUp() {
    // One client per server. Base URLs are bare host:port, so each test spells out the full public
    // path (/api/health) exactly as a browser, Caddy or UptimeRobot would call it.
    // 127.0.0.1, not localhost: the management server binds to IPv4 loopback only, and localhost
    // may resolve to ::1 first on some machines.
    server = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + serverPort).build();
    management =
        RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + managementPort).build();
  }

  @Test
  void publicHealthIsUpWithoutDetails() {
    // Includes the database check: UP means Postgres answered.
    server
        .get()
        .uri("/api/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        // STRICT: the body must be exactly this, so any "components" or "details" fail the test.
        .json("{\"status\":\"UP\"}", JsonCompareMode.STRICT);
  }

  @Test
  void actuatorDoesNotExistOnTheMainPort() {
    // Signed out it is 401 like every unknown path; Actuator only answers on the management port.
    server.get().uri("/api/actuator/health").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void infoOnTheManagementPortShowsGitCommitAndBuildVersion() {
    management
        .get()
        .uri("/actuator/info")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.git.commit.id")
        .isNotEmpty()
        .jsonPath("$.build.version")
        .isNotEmpty();
  }
}
