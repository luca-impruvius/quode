package com.quode.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.quode.shared.ForbiddenException;
import com.quode.shared.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(ProblemDetailsTests.FailingController.class)
@ExtendWith(OutputCaptureExtension.class)
class ProblemDetailsTests {

  @LocalServerPort private int port;

  private RestTestClient client;

  @BeforeEach
  void setUp() {
    client = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
  }

  @Test
  void validationErrorsListTheInvalidFields() {
    EntityExchangeResult<String> result =
        client
            .post()
            .uri("/api/test/validated")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"name\":\"\"}")
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectHeader()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(String.class)
            .returnResult();

    assertThat(result.getResponseBody()).contains("\"field\":\"name\"");
    assertTraceIdInBodyMatchesHeader(result);
  }

  @Test
  void notFoundBecomes404() {
    EntityExchangeResult<String> result = get("/api/test/not-found", 404);

    assertThat(result.getResponseBody()).contains("\"detail\":\"Question not found\"");
    assertTraceIdInBodyMatchesHeader(result);
  }

  @Test
  void unknownPathBecomes404() {
    assertTraceIdInBodyMatchesHeader(get("/api/no-such-path", 404));
  }

  @Test
  void forbiddenBecomes403() {
    assertTraceIdInBodyMatchesHeader(get("/api/test/forbidden", 403));
  }

  @Test
  void unexpectedErrorsHideInternalsButLogThemWithTheTraceId(CapturedOutput output) {
    EntityExchangeResult<String> result = get("/api/test/boom", 500);

    assertThat(result.getResponseBody())
        .doesNotContain("db-password-123")
        .doesNotContain("FailingController")
        .doesNotContain("IllegalStateException");
    assertTraceIdInBodyMatchesHeader(result);
    String traceId = result.getResponseHeaders().getFirst(TraceIdFilter.HEADER);
    assertThat(output).contains("db-password-123").contains(traceId);
  }

  private EntityExchangeResult<String> get(String path, int status) {
    return client
        .get()
        .uri(path)
        .exchange()
        .expectStatus()
        .isEqualTo(status)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(String.class)
        .returnResult();
  }

  private static void assertTraceIdInBodyMatchesHeader(EntityExchangeResult<String> result) {
    String header = result.getResponseHeaders().getFirst(TraceIdFilter.HEADER);
    assertThat(header).isNotBlank();
    assertThat(result.getResponseBody()).contains("\"traceId\":\"" + header + "\"");
  }

  record Named(@NotBlank String name) {}

  // Nested in the test class, so component scanning skips it; only this test imports it.
  @RestController
  static class FailingController {

    @PostMapping("/test/validated")
    void validated(@Valid @RequestBody Named named) {}

    @GetMapping("/test/not-found")
    void notFound() {
      throw new NotFoundException("Question not found");
    }

    @GetMapping("/test/forbidden")
    void forbidden() {
      throw new ForbiddenException();
    }

    @GetMapping("/test/boom")
    void boom() {
      throw new IllegalStateException("connection failed for db-password-123");
    }
  }
}
