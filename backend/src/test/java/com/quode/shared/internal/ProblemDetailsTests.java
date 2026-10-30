package com.quode.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.quode.TestcontainersConfiguration;
import com.quode.shared.ForbiddenException;
import com.quode.shared.NotFoundException;
import jakarta.servlet.Filter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({
  TestcontainersConfiguration.class,
  ProblemDetailsTests.FailingController.class,
  ProblemDetailsTests.FailingFilter.class
})
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
        expectProblem(
            client
                .post()
                .uri("/api/test/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"\"}"),
            400);

    assertThat(result.getResponseBody()).contains("\"field\":\"name\"");
  }

  @Test
  void notFoundBecomes404() {
    EntityExchangeResult<String> result = expectProblem(get("/api/test/not-found"), 404);

    assertThat(result.getResponseBody()).contains("\"detail\":\"Question not found\"");
  }

  @Test
  void unknownPathBecomes404WithoutEchoingThePath() {
    EntityExchangeResult<String> result = expectProblem(get("/api/no-such-path"), 404);

    assertThat(result.getResponseBody())
        .contains("\"detail\":\"Not found\"")
        .doesNotContain("static resource");
  }

  @Test
  void forbiddenBecomes403() {
    expectProblem(get("/api/test/forbidden"), 403);
  }

  @Test
  void exceptionsWithTheirOwnStatusKeepIt() {
    expectProblem(get("/api/test/conflict"), 409);
  }

  @Test
  void unexpectedErrorsHideInternalsButLogThemWithTheTraceId(CapturedOutput output) {
    EntityExchangeResult<String> result = expectProblem(get("/api/test/boom"), 500);

    assertNoInternals(result.getResponseBody());
    assertThat(result.getResponseBody()).doesNotContain("db-password-123");
    String traceId = result.getResponseHeaders().getFirst(TraceIdFilter.HEADER);
    assertThat(output).contains("db-password-123").contains(traceId);
  }

  @Test
  void errorsOutsideMvcAreProblemDetailsWithTheTraceId() {
    EntityExchangeResult<String> result = expectProblem(get("/api/test/filter-boom"), 500);

    assertNoInternals(result.getResponseBody());
    assertThat(result.getResponseBody()).doesNotContain("filter-secret");
  }

  static Stream<Arguments> frameworkErrors() {
    return Stream.of(
        Arguments.of("malformed JSON", HttpMethod.POST, "/api/test/validated", "{bad", 400),
        Arguments.of("wrong method", HttpMethod.GET, "/api/test/validated", null, 405),
        Arguments.of("wrong type", HttpMethod.GET, "/api/test/number/abc", null, 400));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("frameworkErrors")
  void frameworkErrorsAreProblemDetailsWithoutInternals(
      String name, HttpMethod method, String path, String json, int status) {
    RestTestClient.RequestBodySpec request = client.method(method).uri(path);
    if (json != null) {
      request.contentType(MediaType.APPLICATION_JSON).body(json);
    }

    assertNoInternals(expectProblem(request, status).getResponseBody());
  }

  @Test
  void unsupportedMediaTypeIsAProblemDetailWithoutInternals() {
    RestTestClient.RequestHeadersSpec<?> request =
        client
            .post()
            .uri("/api/test/validated")
            .contentType(MediaType.TEXT_PLAIN)
            .body("{\"name\":\"x\"}");

    assertNoInternals(expectProblem(request, 415).getResponseBody());
  }

  private RestTestClient.RequestHeadersSpec<?> get(String path) {
    return client.get().uri(path);
  }

  // Checks the shape every error shares: Problem Details, and the body's traceId equals the header.
  private static EntityExchangeResult<String> expectProblem(
      RestTestClient.RequestHeadersSpec<?> request, int status) {
    EntityExchangeResult<String> result =
        request
            .exchange()
            .expectStatus()
            .isEqualTo(status)
            .expectHeader()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(String.class)
            .returnResult();
    String header = result.getResponseHeaders().getFirst(TraceIdFilter.HEADER);
    assertThat(header).isNotBlank();
    assertThat(result.getResponseBody()).contains("\"traceId\":\"" + header + "\"");
    return result;
  }

  private static void assertNoInternals(String body) {
    assertThat(body)
        .doesNotContain("Exception")
        .doesNotContain("tools.jackson")
        .doesNotContain("java.")
        .doesNotContain("com.quode")
        .doesNotContain("\tat ");
  }

  record Named(@NotBlank String name) {}

  @ResponseStatus(HttpStatus.CONFLICT)
  static class ConflictException extends RuntimeException {}

  // Nested in the test class, so component scanning skips it; only this test imports it.
  @RestController
  static class FailingController {

    @PostMapping("/test/validated")
    void validated(@Valid @RequestBody Named named) {}

    @GetMapping("/test/number/{number}")
    void number(@PathVariable int number) {}

    @GetMapping("/test/not-found")
    void notFound() {
      throw new NotFoundException("Question not found");
    }

    @GetMapping("/test/forbidden")
    void forbidden() {
      throw new ForbiddenException();
    }

    @GetMapping("/test/conflict")
    void conflict() {
      throw new ConflictException();
    }

    @GetMapping("/test/boom")
    void boom() {
      throw new IllegalStateException("connection failed for db-password-123");
    }
  }

  // Fails before Spring MVC is reached, like a broken filter would.
  @TestConfiguration(proxyBeanMethods = false)
  static class FailingFilter {

    @Bean
    FilterRegistrationBean<Filter> failingFilter() {
      FilterRegistrationBean<Filter> registration =
          new FilterRegistrationBean<>(
              (request, response, chain) -> {
                throw new IllegalStateException("filter-secret");
              });
      registration.addUrlPatterns("/test/filter-boom");
      return registration;
    }
  }
}
