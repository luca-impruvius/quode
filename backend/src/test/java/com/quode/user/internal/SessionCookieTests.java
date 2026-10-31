package com.quode.user.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.quode.TestcontainersConfiguration;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

// Real HTTP: the session filter, the cookie attributes and the error dispatch all run as in prod.
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SessionCookieTests {

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate jdbc;

  private RestTestClient client;

  @BeforeEach
  void setUp() {
    client = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
  }

  @Test
  void signInRedirectsToGoogleWithTheApiCallback() {
    ExchangeResult response = startSignIn();

    assertThat(response.getStatus().value()).isEqualTo(302);
    String location =
        URLDecoder.decode(
            response.getResponseHeaders().getFirst(HttpHeaders.LOCATION), StandardCharsets.UTF_8);
    assertThat(location)
        .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
        .contains("redirect_uri=http://127.0.0.1:" + port + "/api/login/oauth2/code/google")
        .contains("scope=openid email profile");
  }

  @Test
  void theSessionCookieIsHttpOnlySecureLaxAndStoredInPostgres() {
    List<String> cookies = startSignIn().getResponseHeaders().get(HttpHeaders.SET_COOKIE);

    String session =
        cookies.stream().filter(c -> c.startsWith("SESSION=")).findFirst().orElseThrow();
    assertThat(session).contains("HttpOnly").contains("Secure").contains("SameSite=Lax");

    // Spring Session's cookie value is the Base64 of the session ID.
    String value = session.substring("SESSION=".length(), session.indexOf(';'));
    String sessionId = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    Integer rows =
        jdbc.queryForObject(
            "select count(*) from spring_session where session_id = ?", Integer.class, sessionId);
    assertThat(rows).isEqualTo(1);
  }

  @Test
  void signedOutApiCallsGet401ProblemDetails() {
    client
        .get()
        .uri("/api/me")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .doesNotExist(HttpHeaders.LOCATION)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401)
        .jsonPath("$.detail")
        .isEqualTo(SecurityConfig.SIGN_IN_REQUIRED)
        .jsonPath("$.instance")
        .isEqualTo("/api/me")
        .jsonPath("$.traceId")
        .isNotEmpty();
  }

  @Test
  void theCsrfTokenCookieIsReadableByTheSpaAndSecureLax() {
    List<String> cookies =
        client
            .get()
            .uri("/api/health")
            .exchange()
            .returnResult(Void.class)
            .getResponseHeaders()
            .get(HttpHeaders.SET_COOKIE);

    String token =
        cookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
    assertThat(token).doesNotContain("HttpOnly").contains("Secure").contains("SameSite=Lax");
  }

  @Test
  void unknownPathsAre401WhenSignedOut() {
    client.get().uri("/api/no-such-path").exchange().expectStatus().isUnauthorized();
  }

  private ExchangeResult startSignIn() {
    return client.get().uri("/api/oauth2/authorization/google").exchange().returnResult(Void.class);
  }
}
