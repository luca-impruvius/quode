package com.quode.question.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quode.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

// Not @Transactional: the dry run must roll back its own, real transaction.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ImportDryRunTests {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  private UUID me;

  @BeforeEach
  void user() {
    me =
        jdbc.queryForObject(
            "insert into app_user (email, google_sub, display_name)"
                + " values ('dry@example.com', 'sub-dry', 'U') returning id",
            UUID.class);
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from question where owner_id = ?", me);
    jdbc.update("delete from topic where owner_id = ? and parent_id is not null", me);
    jdbc.update("delete from topic where owner_id = ?", me);
    jdbc.update("delete from app_user where id = ?", me);
  }

  private void importAs(boolean dryRun) throws Exception {
    String body =
        """
        {"topic":"Cloud › GCP","questions":[
          {"type":"FLASHCARD","prompt":"What is Cloud Run?","answer":"Serverless containers."},
          {"type":"FLASHCARD","prompt":"What is GKE?"}]}
        """;
    mvc.perform(
            post("/api/v1/imports")
                .contextPath("/api")
                .param("dryRun", String.valueOf(dryRun))
                .contentType(MediaType.APPLICATION_JSON)
                .with(oidcLogin().idToken(token -> token.subject("sub-dry")))
                .with(csrf())
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(1))
        .andExpect(jsonPath("$.drafts").value(1))
        .andExpect(jsonPath("$.topicsCreated").value(2));
  }

  private int rows(String table) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where owner_id = ?", Integer.class, me);
  }

  @Test
  void aDryRunSavesNothingAndTheImportThenSavesTheSame() throws Exception {
    importAs(true);
    assertThat(rows("question")).isZero();
    assertThat(rows("topic")).isZero();
    assertThat(rows("review_state")).isZero();

    importAs(false);
    assertThat(rows("question")).isEqualTo(2);
    assertThat(rows("topic")).isEqualTo(2);
    // Both are enrolled for review, the draft too (M3).
    assertThat(rows("review_state")).isEqualTo(2);
  }
}
