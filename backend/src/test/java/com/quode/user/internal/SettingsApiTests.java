package com.quode.user.internal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quode.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** Daily limits on Me (docs/specs/M3-daily-review.md, ADR-0011). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SettingsApiTests {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void users() {
    jdbc.update(
        "insert into app_user (email, google_sub, display_name) values"
            + " ('me@example.com', 'sub-me', 'U'), ('other@example.com', 'sub-other', 'U')");
  }

  private static RequestPostProcessor as(String sub) {
    return oidcLogin().idToken(token -> token.subject(sub));
  }

  private ResultActions save(String sub, String body) throws Exception {
    return mvc.perform(
        put("/api/v1/me/settings")
            .contextPath("/api")
            .contentType(MediaType.APPLICATION_JSON)
            .with(as(sub))
            .with(csrf())
            .content(body));
  }

  private ResultActions settings(String sub) throws Exception {
    return mvc.perform(get("/api/v1/me/settings").contextPath("/api").with(as(sub)));
  }

  @Test
  void theDefaultsAreFiveNewAndThirtyReviews() throws Exception {
    settings("sub-me")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newPerDay").value(5))
        .andExpect(jsonPath("$.reviewsPerDay").value(30));
  }

  @Test
  void savedLimitsAreTheUsersOwn() throws Exception {
    save("sub-me", "{\"newPerDay\":0,\"reviewsPerDay\":500}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newPerDay").value(0))
        .andExpect(jsonPath("$.reviewsPerDay").value(500));
    settings("sub-me").andExpect(jsonPath("$.reviewsPerDay").value(500));
    settings("sub-other").andExpect(jsonPath("$.reviewsPerDay").value(30));
  }

  @Test
  void limitsOutOfBoundsOrMissingAreRefused() throws Exception {
    for (String body :
        List.of(
            "{\"newPerDay\":-1,\"reviewsPerDay\":30}",
            "{\"newPerDay\":101,\"reviewsPerDay\":30}",
            "{\"newPerDay\":5,\"reviewsPerDay\":0}",
            "{\"newPerDay\":5,\"reviewsPerDay\":501}",
            "{\"newPerDay\":5}",
            "{}")) {
      save("sub-me", body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(SettingsController.OUT_OF_BOUNDS));
    }
    settings("sub-me").andExpect(jsonPath("$.newPerDay").value(5));
  }

  @Test
  void settingsNeedASignedInUser() throws Exception {
    mvc.perform(get("/api/v1/me/settings").contextPath("/api"))
        .andExpect(status().isUnauthorized());
  }
}
