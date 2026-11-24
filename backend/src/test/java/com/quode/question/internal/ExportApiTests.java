package com.quode.question.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quode.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** Export in the import format, and filling answers back by id (M2-02). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ExportApiTests {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  private UUID me;

  @BeforeEach
  void data() throws Exception {
    me = user("me@example.com", "sub-me");
    user("other@example.com", "sub-other");
    String questions =
        """
        {"topic":"Cloud › GCP","questions":[
          {"type":"SINGLE_CHOICE","prompt":"Unanswered GCP?",
           "options":[{"text":"A","correct":false},{"text":"B","correct":false}],
           "topics":["Networking","Exam"]},
          {"type":"FLASHCARD","prompt":"Answered GCP?","answer":"Yes."},
          {"type":"FLASHCARD","prompt":"Unanswered card?","explanation":"Hint.","difficulty":3}]}
        """;
    importAs(me(), questions).andExpect(jsonPath("$.drafts").value(2));
    importAs(
            me(),
            "{\"topic\":\"Networking\",\"questions\":[{\"type\":\"OPEN_TEXT\",\"prompt\":\"Net draft?\"}]}")
        .andExpect(jsonPath("$.drafts").value(1));
  }

  private UUID user(String email, String sub) {
    return jdbc.queryForObject(
        "insert into app_user (email, google_sub, display_name) values (?, ?, 'U') returning id",
        UUID.class,
        email,
        sub);
  }

  private static RequestPostProcessor me() {
    return oidcLogin().idToken(token -> token.subject("sub-me"));
  }

  private static RequestPostProcessor other() {
    return oidcLogin().idToken(token -> token.subject("sub-other"));
  }

  private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder request) {
    return request.contextPath("/api").contentType(MediaType.APPLICATION_JSON);
  }

  private ResultActions importAs(RequestPostProcessor user, String body) throws Exception {
    return mvc.perform(api(post("/api/v1/imports")).with(user).with(csrf()).content(body));
  }

  private ResultActions export(RequestPostProcessor user, String query) throws Exception {
    return mvc.perform(api(get("/api/v1/questions/export" + query)).with(user));
  }

  private UUID idOf(String prompt) {
    return jdbc.queryForObject(
        "select id from question where owner_id = ? and prompt_md = ?", UUID.class, me, prompt);
  }

  private UUID topic(String name) {
    return jdbc.queryForObject(
        "select id from topic where owner_id = ? and name = ?", UUID.class, me, name);
  }

  @Test
  void exportsDraftsWithIdsInTheImportFormat() throws Exception {
    export(me(), "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.topic").value(nullValue()))
        .andExpect(jsonPath("$.questions.length()").value(3))
        .andExpect(jsonPath("$.questions[0].id").value(idOf("Unanswered GCP?").toString()))
        .andExpect(jsonPath("$.questions[0].type").value("SINGLE_CHOICE"))
        .andExpect(jsonPath("$.questions[0].prompt").value("Unanswered GCP?"))
        .andExpect(jsonPath("$.questions[0].options[1].text").value("B"))
        .andExpect(jsonPath("$.questions[0].options[1].correct").value(false))
        .andExpect(jsonPath("$.questions[0].topics[0]").value("Cloud › GCP"))
        .andExpect(jsonPath("$.questions[0].topics[1]").value("Exam"))
        .andExpect(jsonPath("$.questions[0].topics[2]").value("Networking"))
        .andExpect(jsonPath("$.questions[1].prompt").value("Unanswered card?"))
        .andExpect(jsonPath("$.questions[1].explanation").value("Hint."))
        .andExpect(jsonPath("$.questions[1].difficulty").value(3))
        .andExpect(jsonPath("$.questions[1].answer").value(nullValue()))
        .andExpect(jsonPath("$.questions[2].prompt").value("Net draft?"))
        .andExpect(jsonPath("$.remaining").value(0));
  }

  @Test
  void filtersByStatusTopicSubtreeAndLeavesArchivedOut() throws Exception {
    export(me(), "?status=active")
        .andExpect(jsonPath("$.questions.length()").value(1))
        .andExpect(jsonPath("$.questions[0].answer").value("Yes."));

    export(me(), "?topicId=" + topic("Cloud")).andExpect(jsonPath("$.questions.length()").value(2));
    export(me(), "?topicId=" + topic("Networking"))
        .andExpect(jsonPath("$.questions.length()").value(2));

    jdbc.update("update question set archived_at = now() where id = ?", idOf("Net draft?"));
    export(me(), "").andExpect(jsonPath("$.questions.length()").value(2));
  }

  @Test
  void exportsOnlyTheUsersOwnQuestions() throws Exception {
    export(other(), "").andExpect(jsonPath("$.questions").isEmpty());
    export(other(), "?topicId=" + topic("Cloud")).andExpect(status().isNotFound());
  }

  @Test
  void rejectsAnUnknownStatusAndNeedsSignIn() throws Exception {
    export(me(), "?status=ARCHIVED").andExpect(status().isBadRequest());
    mvc.perform(api(get("/api/v1/questions/export"))).andExpect(status().isUnauthorized());
  }

  @Test
  void anExportWithAnswersAddedImportsBackAsFilledQuestions() throws Exception {
    String exported =
        export(me(), "?topicId=" + topic("GCP"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    // Answer them the way Claude would outside the app: mark B correct, add the card's answer.
    String answered =
        exported
            .replace("{\"text\":\"B\",\"correct\":false}", "{\"text\":\"B\",\"correct\":true}")
            .replace(
                "\"prompt\":\"Unanswered card?\",\"answer\":null",
                "\"prompt\":\"Unanswered card?\",\"answer\":\"Filled.\"");

    importAs(me(), answered)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.filled").value(2))
        .andExpect(jsonPath("$.created").value(0))
        .andExpect(jsonPath("$.topicsCreated").value(0))
        .andExpect(jsonPath("$.problems").isEmpty());

    export(me(), "")
        .andExpect(jsonPath("$.questions.length()").value(1))
        .andExpect(jsonPath("$.questions[0].prompt").value("Net draft?"));
    assertThat(
            jdbc.queryForObject(
                "select answer_md from question where id = ?",
                String.class,
                idOf("Unanswered card?")))
        .isEqualTo("Filled.");
    assertThat(
            jdbc.queryForObject(
                "select answer_md from question where id = ?",
                String.class,
                idOf("Unanswered GCP?")))
        .isNull();
  }

  @Test
  void anExportHoldsAtMostOneImportAndCountsTheRest() throws Exception {
    UUID gcp = topic("GCP");
    jdbc.update(
        "insert into question (owner_id, type, prompt_md, status, primary_topic_id)"
            + " select ?, 'FLASHCARD', 'Bulk ' || g, 'DRAFT', ? from generate_series(1, 2001) g",
        me,
        gcp);
    jdbc.update(
        "insert into question_topic (question_id, topic_id, owner_id)"
            + " select id, primary_topic_id, owner_id from question"
            + " where owner_id = ? and prompt_md like 'Bulk %'",
        me);

    export(me(), "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.questions.length()").value(ExportService.MAX_QUESTIONS))
        .andExpect(jsonPath("$.remaining").value(2004 - ExportService.MAX_QUESTIONS));
  }
}
