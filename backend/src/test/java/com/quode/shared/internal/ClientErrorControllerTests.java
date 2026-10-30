package com.quode.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class ClientErrorControllerTests {

  @LocalServerPort private int port;

  private RestTestClient client;

  @BeforeEach
  void setUp() {
    client = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
  }

  @Test
  void logsTheReportWithTheTraceId(CapturedOutput output) {
    String traceId =
        post("{\"message\":\"TypeError: x is undefined\",\"url\":\"/review\",\"stack\":\"at f\"}")
            .expectStatus()
            .isNoContent()
            .returnResult(Void.class)
            .getResponseHeaders()
            .getFirst(TraceIdFilter.HEADER);

    assertThat(traceId).isNotBlank();
    assertThat(output).contains("TypeError: x is undefined").contains("/review").contains(traceId);
  }

  @Test
  void truncatesLongFields(CapturedOutput output) {
    String longMessage = "m".repeat(ClientErrorReport.MAX_MESSAGE + 50);

    post("{\"message\":\"" + longMessage + "\"}").expectStatus().isNoContent();

    assertThat(output)
        .contains("m".repeat(ClientErrorReport.MAX_MESSAGE))
        .doesNotContain("m".repeat(ClientErrorReport.MAX_MESSAGE + 1));
  }

  @Test
  void rejectsBodiesOverTheSizeLimit(CapturedOutput output) {
    String hugeStack = "s".repeat(ClientErrorController.MAX_BODY_BYTES);

    post("{\"message\":\"big\",\"stack\":\"" + hugeStack + "\"}")
        .expectStatus()
        .isEqualTo(413)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(output).doesNotContain("s".repeat(ClientErrorReport.MAX_STACK));
  }

  @Test
  void rejectsMalformedJson() {
    post("{not json")
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void requiresAMessage() {
    post("{\"url\":\"/review\"}")
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  private RestTestClient.ResponseSpec post(String json) {
    return client
        .post()
        .uri("/api/client-errors")
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange();
  }
}
