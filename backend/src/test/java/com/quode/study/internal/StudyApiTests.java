package com.quode.study.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.quode.TestcontainersConfiguration;
import java.util.List;
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

/** Quick quizzes as ADHOC study sessions (docs/specs/M2-exam-prep-mvp.md, M2-05). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class StudyApiTests {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  private UUID me;
  private UUID gcp;

  @BeforeEach
  void data() throws Exception {
    me = user("me@example.com", "sub-me");
    user("other@example.com", "sub-other");
    // Oldest first: single, multiple, flashcard; then a draft and an archived one (never asked).
    String file =
        """
        {"topic":"Cloud › GCP","questions":[
          {"type":"SINGLE_CHOICE","prompt":"Serverless containers?",
           "options":[{"text":"GKE","correct":false},{"text":"Cloud Run","correct":true}],
           "explanation":"No cluster."},
          {"type":"MULTIPLE_CHOICE","prompt":"Managed databases?",
           "options":[{"text":"Cloud SQL","correct":true},{"text":"Compute Engine","correct":false},
                      {"text":"Spanner","correct":true}]},
          {"type":"FLASHCARD","prompt":"What is IAM?","answer":"Access control."},
          {"type":"FLASHCARD","prompt":"Draft card?"},
          {"type":"FLASHCARD","prompt":"Archived card?","answer":"x"}]}
        """;
    send(post("/api/v1/imports"), me(), file).andExpect(jsonPath("$.created").value(4));
    jdbc.update("update question set archived_at = now() where prompt_md = 'Archived card?'");
    gcp =
        jdbc.queryForObject(
            "select id from topic where owner_id = ? and name = 'GCP'", UUID.class, me);
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

  private ResultActions send(
      MockHttpServletRequestBuilder request, RequestPostProcessor user, String body)
      throws Exception {
    return mvc.perform(
        request
            .contextPath("/api")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user)
            .with(csrf())
            .content(body));
  }

  private UUID question(String prompt) {
    return jdbc.queryForObject(
        "select id from question where owner_id = ? and prompt_md = ?", UUID.class, me, prompt);
  }

  private UUID option(String text) {
    return jdbc.queryForObject(
        "select o.id from answer_option o join question q on q.id = o.question_id"
            + " where q.owner_id = ? and o.text_md = ?",
        UUID.class,
        me,
        text);
  }

  private String start(String body) throws Exception {
    String json =
        send(post("/api/v1/study/sessions"), me(), body)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(json, "$.id");
  }

  private String fromGcp() throws Exception {
    return start("{\"kind\":\"ADHOC\",\"topicIds\":[\"" + gcp + "\"]}");
  }

  private ResultActions choose(String session, String prompt, String... options) throws Exception {
    StringBuilder ids = new StringBuilder();
    for (String o : options) {
      ids.append(ids.isEmpty() ? "" : ",").append('"').append(option(o)).append('"');
    }
    return send(
        post("/api/v1/study/sessions/" + session + "/answers"),
        me(),
        "{\"questionId\":\"%s\",\"selectedOptionIds\":[%s]}".formatted(question(prompt), ids));
  }

  private ResultActions grade(String session, String prompt, String grade) throws Exception {
    return send(
        post("/api/v1/study/sessions/" + session + "/answers"),
        me(),
        "{\"questionId\":\"%s\",\"grade\":\"%s\"}".formatted(question(prompt), grade));
  }

  private ResultActions finish(String session) throws Exception {
    return send(post("/api/v1/study/sessions/" + session + "/finish"), me(), "");
  }

  @Test
  void aQuizAsksTheTopicsStudyableQuestionsInOrderWithoutTheirAnswers() throws Exception {
    send(post("/api/v1/study/sessions"), me(), "{\"topicIds\":[\"" + gcp + "\"],\"count\":20}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.kind").value("ADHOC"))
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.answered").value(0))
        .andExpect(jsonPath("$.current.promptMd").value("Serverless containers?"))
        .andExpect(jsonPath("$.current.type").value("SINGLE_CHOICE"))
        .andExpect(jsonPath("$.current.options[1].textMd").value("Cloud Run"))
        .andExpect(jsonPath("$.current.options[1].correct").doesNotExist())
        .andExpect(jsonPath("$.current.answerMd").doesNotExist());
  }

  @Test
  void gradesChoicesAtOnceAndSelfGradesTheRest() throws Exception {
    String session = fromGcp();

    choose(session, "Serverless containers?", "Cloud Run")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("CORRECT"))
        .andExpect(jsonPath("$.correctOptionIds[0]").value(option("Cloud Run").toString()))
        .andExpect(jsonPath("$.explanationMd").value("No cluster."))
        .andExpect(jsonPath("$.session.answered").value(1))
        .andExpect(jsonPath("$.session.current.promptMd").value("Managed databases?"));

    // All-or-nothing: one of the two right options is wrong.
    choose(session, "Managed databases?", "Cloud SQL")
        .andExpect(jsonPath("$.outcome").value("WRONG"))
        .andExpect(jsonPath("$.correctOptionIds.length()").value(2));

    grade(session, "What is IAM?", "PARTIAL")
        .andExpect(jsonPath("$.outcome").value("PARTIAL"))
        .andExpect(jsonPath("$.answerMd").value("Access control."))
        .andExpect(jsonPath("$.session.current").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.session.correct").value(1));

    assertThat(
            jdbc.queryForList(
                "select graded_by from attempt where owner_id = ? order by answered_at, id",
                String.class,
                me))
        .containsExactly("AUTO", "AUTO", "SELF");
  }

  @Test
  void finishingScoresAndRetryMissedAsksOnlyThoseQuestions() throws Exception {
    String session = fromGcp();
    choose(session, "Serverless containers?", "GKE");
    choose(session, "Managed databases?", "Cloud SQL", "Spanner");
    grade(session, "What is IAM?", "MISSED");

    finish(session)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.answered").value(3))
        .andExpect(jsonPath("$.correct").value(1))
        .andExpect(jsonPath("$.wrong").value(2))
        .andExpect(jsonPath("$.missedQuestionIds.length()").value(2))
        .andExpect(
            jsonPath("$.missedQuestionIds[0]")
                .value(question("Serverless containers?").toString()));

    String retry =
        start(
            "{\"questionIds\":[\"%s\",\"%s\"]}"
                .formatted(question("Serverless containers?"), question("What is IAM?")));
    mvc.perform(get("/api/v1/study/sessions/" + retry).contextPath("/api").with(me()))
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.current.promptMd").value("Serverless containers?"));
  }

  @Test
  void aQuizResumesAtTheSameQuestion() throws Exception {
    String session = fromGcp();
    choose(session, "Serverless containers?", "Cloud Run");

    mvc.perform(get("/api/v1/study/sessions/" + session).contextPath("/api").with(me()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answered").value(1))
        .andExpect(jsonPath("$.current.promptMd").value("Managed databases?"));
  }

  @Test
  void answersMustFollowTheOrderAndStopWhenFinished() throws Exception {
    String session = fromGcp();
    grade(session, "What is IAM?", "GOT_IT").andExpect(status().isConflict());
    choose(session, "Serverless containers?", "Cloud Run").andExpect(status().isOk());
    choose(session, "Serverless containers?", "Cloud Run").andExpect(status().isConflict());

    finish(session).andExpect(status().isOk());
    choose(session, "Managed databases?", "Cloud SQL").andExpect(status().isConflict());
    finish(session).andExpect(jsonPath("$.answered").value(1));
  }

  @Test
  void answersMustFitTheQuestionType() throws Exception {
    String session = fromGcp();
    grade(session, "Serverless containers?", "GOT_IT").andExpect(status().isBadRequest());
    send(
            post("/api/v1/study/sessions/" + session + "/answers"),
            me(),
            "{\"questionId\":\"%s\",\"selectedOptionIds\":[\"%s\"]}"
                .formatted(question("Serverless containers?"), option("Spanner")))
        .andExpect(status().isBadRequest());
    choose(session, "Serverless containers?", "Cloud Run");
    choose(session, "Managed databases?", "Cloud SQL", "Spanner");
    grade(session, "What is IAM?", "MAYBE").andExpect(status().isBadRequest());
  }

  @Test
  void unseenAndMissedPickByTheLatestAnswer() throws Exception {
    String first = fromGcp();
    choose(first, "Serverless containers?", "GKE");
    choose(first, "Managed databases?", "Cloud SQL", "Spanner");

    String unseen = start("{\"topicIds\":[\"" + gcp + "\"],\"mode\":\"UNSEEN\"}");
    mvc.perform(get("/api/v1/study/sessions/" + unseen).contextPath("/api").with(me()))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.current.promptMd").value("What is IAM?"));

    String missed = start("{\"topicIds\":[\"" + gcp + "\"],\"mode\":\"missed\"}");
    mvc.perform(get("/api/v1/study/sessions/" + missed).contextPath("/api").with(me()))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.current.promptMd").value("Serverless containers?"));
  }

  @Test
  void countAndShuffleShapeTheOrder() throws Exception {
    String two = start("{\"topicIds\":[\"" + gcp + "\"],\"count\":2,\"shuffle\":true}");
    List<UUID> order =
        jdbc.queryForList(
            "select unnest(question_order) from study_session where id = ?::uuid", UUID.class, two);
    assertThat(order).hasSize(2).doesNotHaveDuplicates();
    assertThat(
            List.of(
                question("Serverless containers?"),
                question("Managed databases?"),
                question("What is IAM?")))
        .containsAll(order);
  }

  @Test
  void rejectsBadStartsAndEmptyQuizzes() throws Exception {
    for (String body :
        List.of(
            "{}",
            "{\"kind\":\"REVIEW\",\"topicIds\":[\"" + gcp + "\"]}",
            "{\"topicIds\":[\"" + gcp + "\"],\"count\":0}",
            "{\"topicIds\":[\"" + gcp + "\"],\"mode\":\"SOMETIMES\"}",
            "{\"topicIds\":[\"" + gcp + "\"],\"questionIds\":[\"" + UUID.randomUUID() + "\"]}")) {
      send(post("/api/v1/study/sessions"), me(), body).andExpect(status().isBadRequest());
    }
    send(post("/api/v1/study/sessions"), other(), "{\"topicIds\":[\"" + gcp + "\"]}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(StudyService.NONE_MATCH));
  }

  @Test
  void sessionsAreTheirOwnersOnly() throws Exception {
    String session = fromGcp();
    mvc.perform(get("/api/v1/study/sessions/" + session).contextPath("/api").with(other()))
        .andExpect(status().isNotFound());
    send(post("/api/v1/study/sessions/" + session + "/finish"), other(), "")
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/study/sessions/" + session).contextPath("/api"))
        .andExpect(status().isUnauthorized());
  }
}
