package com.quode.question.internal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.quode.TestcontainersConfiguration;
import java.util.UUID;
import org.hamcrest.Matchers;
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

/** Flag for fixing (docs/specs/M1-questions.md): flag with a note, unflag, edits clear the flag. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class FlagApiTests {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  private UUID java;
  private String id;

  @BeforeEach
  void data() throws Exception {
    UUID me = user("me@example.com", "sub-me");
    user("other@example.com", "sub-other");
    java =
        jdbc.queryForObject(
            "insert into topic (owner_id, name) values (?, 'Java') returning id", UUID.class, me);
    String json =
        mvc.perform(
                post("/api/v1/questions")
                    .contextPath("/api")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(as("sub-me"))
                    .with(csrf())
                    .content(flashcard("What is a HashMap?", null)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    id = JsonPath.read(json, "$.id");
  }

  private UUID user(String email, String sub) {
    return jdbc.queryForObject(
        "insert into app_user (email, google_sub, display_name) values (?, ?, 'U') returning id",
        UUID.class,
        email,
        sub);
  }

  private static RequestPostProcessor as(String sub) {
    return oidcLogin().idToken(token -> token.subject(sub));
  }

  private String flashcard(String prompt, Integer version) {
    return """
        {"type":"FLASHCARD","promptMd":"%s","answerMd":"A hash table.","difficulty":2,
         "topicIds":["%s"],"primaryTopicId":"%s"%s}
        """
        .formatted(prompt, java, java, version == null ? "" : ",\"version\":" + version);
  }

  private ResultActions action(String sub, String path, String body) throws Exception {
    return mvc.perform(
        post("/api/v1/questions/" + id + "/" + path)
            .contextPath("/api")
            .contentType(MediaType.APPLICATION_JSON)
            .with(as(sub))
            .with(csrf())
            .content(body));
  }

  @Test
  void flagsWithANoteAndUnflags() throws Exception {
    action("sub-me", "flag", "{\"version\":0,\"note\":\"  Typo in the answer  \"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flaggedAt").isNotEmpty())
        .andExpect(jsonPath("$.flagNote").value("Typo in the answer"))
        .andExpect(jsonPath("$.version").value(1));

    action("sub-me", "unflag", "{\"version\":1}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flaggedAt").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.flagNote").value(Matchers.nullValue()));
  }

  @Test
  void theNoteIsOptionalAndUpTo200Characters() throws Exception {
    action("sub-me", "flag", "{\"version\":0}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flagNote").value(Matchers.nullValue()));
    action("sub-me", "flag", "{\"version\":1,\"note\":\"%s\"}".formatted("x".repeat(201)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(QuestionService.NOTE_TOO_LONG));
    action("sub-me", "flag", "{\"version\":1,\"note\":\"%s\"}".formatted("x".repeat(200)))
        .andExpect(status().isOk());
  }

  @Test
  void flaggingNeedsTheCurrentVersion() throws Exception {
    action("sub-me", "flag", "{\"version\":7}").andExpect(status().isConflict());
    action("sub-me", "flag", "{}").andExpect(status().isBadRequest());
  }

  @Test
  void savingAnEditClearsTheFlag() throws Exception {
    action("sub-me", "flag", "{\"version\":0,\"note\":\"Fix me\"}").andExpect(status().isOk());

    mvc.perform(
            put("/api/v1/questions/" + id)
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .with(as("sub-me"))
                .with(csrf())
                .content(flashcard("What is a HashMap, really?", 1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flaggedAt").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.flagNote").value(Matchers.nullValue()));
  }

  @Test
  void theFlaggedFilterListsFlaggedQuestionsWithTheirNote() throws Exception {
    action("sub-me", "flag", "{\"version\":0,\"note\":\"Check the source\"}")
        .andExpect(status().isOk());

    mvc.perform(get("/api/v1/questions?status=flagged").contextPath("/api").with(as("sub-me")))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].flagged").value(true))
        .andExpect(jsonPath("$.items[0].flagNote").value("Check the source"));
  }

  @Test
  void aStaleUnflagIs409() throws Exception {
    action("sub-me", "flag", "{\"version\":0}").andExpect(status().isOk());

    action("sub-me", "unflag", "{\"version\":0}").andExpect(status().isConflict());
  }

  @Test
  void archiveAndUnarchiveKeepTheFlag() throws Exception {
    action("sub-me", "flag", "{\"version\":0,\"note\":\"Keep me\"}").andExpect(status().isOk());

    action("sub-me", "archive", "{\"version\":1}")
        .andExpect(jsonPath("$.flagNote").value("Keep me"));
    action("sub-me", "unarchive", "{\"version\":2}")
        .andExpect(jsonPath("$.flaggedAt").isNotEmpty())
        .andExpect(jsonPath("$.flagNote").value("Keep me"));
  }

  @Test
  void anotherUsersFlaggedFilterNeverShowsMyQuestion() throws Exception {
    action("sub-me", "flag", "{\"version\":0}").andExpect(status().isOk());

    mvc.perform(get("/api/v1/questions?status=flagged").contextPath("/api").with(as("sub-other")))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void anotherUserCantFlagMyQuestion() throws Exception {
    action("sub-other", "flag", "{\"version\":0}").andExpect(status().isNotFound());
    action("sub-other", "unflag", "{\"version\":0}").andExpect(status().isNotFound());
  }

  @Test
  void flaggingNeedsTheCsrfToken() throws Exception {
    mvc.perform(
            post("/api/v1/questions/" + id + "/flag")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .with(as("sub-me"))
                .content("{\"version\":0}"))
        .andExpect(status().isForbidden());
  }
}
