package com.quode.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TraceIdTests {

  private static final String TRACE_ID = "[0-9a-f]{32}";

  @LocalServerPort private int port;

  private RestTestClient client;

  @BeforeEach
  void setUp() {
    client = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
  }

  @Test
  void everyResponseCarriesATraceIdHeader() {
    String first = traceIdOf(client.get().uri("/api/health"));
    String second = traceIdOf(client.get().uri("/api/health"));

    assertThat(first).matches(TRACE_ID);
    assertThat(second).matches(TRACE_ID).isNotEqualTo(first);
  }

  private static String traceIdOf(RestTestClient.RequestHeadersSpec<?> request) {
    return request
        .exchange()
        .returnResult(Void.class)
        .getResponseHeaders()
        .getFirst(TraceIdFilter.HEADER);
  }
}
