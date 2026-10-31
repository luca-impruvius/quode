package com.quode;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

// The frontend generates its API types from this spec (npm run generate:api).
class OpenApiDocsTests {

  private static RestTestClient client(int port) {
    return RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
  }

  @Nested
  @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
  @Import(TestcontainersConfiguration.class)
  class Locally {

    @LocalServerPort private int port;

    @Test
    void servesTheSpec() {
      client(port)
          .get()
          .uri("/api/v3/api-docs")
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.openapi")
          .isNotEmpty();
    }
  }

  // The prod profile has no datasource defaults; the placeholders satisfy it, and the container's
  // service connection is what the app actually uses.
  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      properties = {"DB_URL=unused", "DB_USER=unused", "DB_PASSWORD=unused"})
  @Import(TestcontainersConfiguration.class)
  @ActiveProfiles("prod")
  class InProduction {

    @LocalServerPort private int port;

    @Test
    void hidesTheSpec() {
      // Not permitted in prod, so security answers before the (disabled) endpoint would 404.
      client(port).get().uri("/api/v3/api-docs").exchange().expectStatus().isUnauthorized();
    }
  }
}
