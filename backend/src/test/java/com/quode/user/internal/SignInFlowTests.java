package com.quode.user.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quode.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The whole callback through the real login filters: only Google's token endpoint and ID-token
 * signature check are stubbed. Proves the allowlist and the failure handler are wired in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SignInFlowTests.FakeGoogle.class})
class SignInFlowTests {

  // The ID token "Google" returns next; the nonce is filled in from the authorization request.
  private static final Map<String, Object> idTokenClaims = new HashMap<>();

  @Autowired private MockMvc mvc;

  @Autowired private AppUserRepository users;

  @AfterEach
  void cleanUp() {
    users.deleteAll();
  }

  @Test
  void anAllowedAccountIsSignedInAndSentToTheAppRoot() throws Exception {
    MvcResult callback = signInAs("sub-flow", "Allowed@Example.com");

    assertThat(callback.getResponse().getStatus()).isEqualTo(302);
    assertThat(callback.getResponse().getRedirectedUrl()).isEqualTo("/");
    assertThat(users.findByGoogleSub("sub-flow")).isPresent();

    mvc.perform(get("/api/me").contextPath("/api").cookie(sessionCookie(callback)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("allowed@example.com"));
  }

  @Test
  void anAccountNotOnTheListGoesBackToLoginAndGetsNoUser() throws Exception {
    MvcResult callback = signInAs("sub-other", "someone@example.com");

    assertThat(callback.getResponse().getStatus()).isEqualTo(302);
    assertThat(callback.getResponse().getRedirectedUrl()).isEqualTo("/login?error=no-access");
    assertThat(users.findByGoogleSub("sub-other")).isEmpty();
  }

  private MvcResult signInAs(String sub, String email) throws Exception {
    MvcResult start =
        mvc.perform(get("/api/oauth2/authorization/google").contextPath("/api")).andReturn();
    Map<String, String> query = queryOf(start.getResponse().getRedirectedUrl());

    idTokenClaims.clear();
    idTokenClaims.putAll(
        Map.of(
            "iss",
            "https://accounts.google.com",
            "aud",
            "test-client-id",
            "sub",
            sub,
            "email",
            email,
            "email_verified",
            true,
            "name",
            "Flow User",
            // Google echoes the (already hashed) nonce from the authorization request.
            "nonce",
            query.get("nonce"),
            "iat",
            Instant.now(),
            "exp",
            Instant.now().plusSeconds(60)));

    return mvc.perform(
            get("/api/login/oauth2/code/google")
                .contextPath("/api")
                .param("code", "fake-code")
                .param("state", query.get("state"))
                .cookie(sessionCookie(start)))
        .andReturn();
  }

  private static Cookie sessionCookie(MvcResult result) {
    Cookie cookie = result.getResponse().getCookie("SESSION");
    assertThat(cookie).as("SESSION cookie").isNotNull();
    return cookie;
  }

  private static Map<String, String> queryOf(String url) {
    Map<String, String> query = new HashMap<>();
    UriComponentsBuilder.fromUriString(url)
        .build()
        .getQueryParams()
        .forEach((k, v) -> query.put(k, URLDecoder.decode(v.getFirst(), StandardCharsets.UTF_8)));
    return query;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FakeGoogle {

    @Bean
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> fakeTokenEndpoint() {
      return request ->
          OAuth2AccessTokenResponse.withToken("fake-access-token")
              .tokenType(OAuth2AccessToken.TokenType.BEARER)
              .expiresIn(60)
              .scopes(Set.of("openid"))
              .additionalParameters(Map.of("id_token", "fake-id-token"))
              .build();
    }

    // No userinfo call to the real Google: the ID token carries everything the app needs.
    @Bean
    @Primary
    OidcUserService idTokenOnlyUserService() {
      OidcUserService service = new OidcUserService();
      service.setRetrieveUserInfo(request -> false);
      return service;
    }

    @Bean
    JwtDecoderFactory<ClientRegistration> fakeIdTokenDecoder() {
      return registration ->
          token ->
              Jwt.withTokenValue(token)
                  .header("alg", "RS256")
                  .claims(claims -> claims.putAll(idTokenClaims))
                  .build();
    }
  }
}
