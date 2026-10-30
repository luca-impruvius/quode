package com.quode;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

// The frontend generates its API types from this spec (npm run generate:api).
class OpenApiDocsTests {

  private static RestTestClient client(int port) {
    return RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
  }

  @Nested
  @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
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

  @Nested
  @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
  @ActiveProfiles("prod")
  class InProduction {

    @LocalServerPort private int port;

    @Test
    void hidesTheSpec() {
      client(port).get().uri("/api/v3/api-docs").exchange().expectStatus().isNotFound();
    }
  }
}
