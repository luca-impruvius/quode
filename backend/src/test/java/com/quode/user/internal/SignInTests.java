package com.quode.user.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quode.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SignInTests {

  @Autowired private MockMvc mvc;

  @Autowired private AppUserRepository repository;

  @Test
  void meWhenSignedOutIs401NotARedirect() throws Exception {
    // Real HTTP renders the Problem Details body through the error dispatch (see
    // SessionCookieTests); MockMvc only records the status and the detail attribute.
    mvc.perform(get("/api/me").contextPath("/api"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist("Location"));
  }

  @Test
  void meWhenSignedInShowsNameEmailAndTimeZone() throws Exception {
    repository.saveAndFlush(new AppUser("allowed@example.com", "sub-1", "Test User"));

    mvc.perform(
            get("/api/me")
                .contextPath("/api")
                .with(oidcLogin().idToken(token -> token.subject("sub-1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test User"))
        .andExpect(jsonPath("$.email").value("allowed@example.com"))
        .andExpect(jsonPath("$.timeZone").value("Europe/Bucharest"));
  }

  @Test
  void meForASessionWhoseUserIsGoneIs401() throws Exception {
    mvc.perform(
            get("/api/me")
                .contextPath("/api")
                .with(oidcLogin().idToken(token -> token.subject("no-such-sub"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutNeedsTheCsrfToken() throws Exception {
    mvc.perform(post("/api/logout").contextPath("/api").with(oidcLogin()))
        .andExpect(status().isForbidden());
  }

  @Test
  void logoutWithTheCsrfTokenIs204() throws Exception {
    mvc.perform(post("/api/logout").contextPath("/api").with(oidcLogin()).with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  void clientErrorReportsNeedNoSignInAndNoCsrfToken() throws Exception {
    mvc.perform(
            post("/api/client-errors")
                .contextPath("/api")
                .contentType("application/json")
                .content("{\"message\":\"boom\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void aBlockedAccountGoesBackToLoginWithNoAccess() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    new SignInFailureHandler()
        .onAuthenticationFailure(
            request,
            response,
            new OAuth2AuthenticationException(
                new OAuth2Error(AllowlistOidcUserService.NOT_ALLOWED)));

    assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=no-access");
  }

  @Test
  void cancellingAtGoogleGoesBackToLoginWithFailed() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    new SignInFailureHandler()
        .onAuthenticationFailure(
            request, response, new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

    assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=failed");
  }
}
