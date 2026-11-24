package com.quode.question.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quode.TestcontainersConfiguration;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

/** Bulk import (docs/specs/M2-exam-prep-mvp.md, M2-01). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ImportApiTests {

  private static final String GCP = "Cloud › GCP › Professional Cloud Developer";

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  private UUID me;
  private UUID other;

  @BeforeEach
  void users() {
    me = user("me@example.com", "sub-me");
    other = user("other@example.com", "sub-other");
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

  private ResultActions importAs(RequestPostProcessor user, boolean dryRun, String body)
      throws Exception {
    return mvc.perform(
        post("/api/v1/imports")
            .contextPath("/api")
            .param("dryRun", String.valueOf(dryRun))
            .contentType(MediaType.APPLICATION_JSON)
            .with(user)
            .with(csrf())
            .content(body));
  }

  private ResultActions importJson(String body) throws Exception {
    return importAs(me(), false, body);
  }

  private static String file(String topic, String... questions) {
    String topicJson = topic == null ? "null" : "\"" + topic + "\"";
    return "{\"topic\":" + topicJson + ",\"questions\":[" + String.join(",", questions) + "]}";
  }

  private static String single(String prompt) {
    return """
        {"type":"SINGLE_CHOICE","prompt":"%s",
         "options":[{"text":"Cloud Run","correct":true},{"text":"GKE","correct":false}],
         "explanation":"Serverless.","difficulty":3,"sourceUrl":"https://example.com/q"}
        """
        .formatted(prompt);
  }

  private static String unanswered(String prompt) {
    return """
        {"type":"SINGLE_CHOICE","prompt":"%s",
         "options":[{"text":"A","correct":false},{"text":"B"}]}
        """
        .formatted(prompt);
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  private UUID onlyQuestion() {
    return jdbc.queryForObject("select id from question where owner_id = ?", UUID.class, me);
  }

  private int questionsOf(UUID owner) {
    return count("select count(*) from question where owner_id = ?", owner);
  }

  @Test
  void importsQuestionsUnderTheFileTopicCreatingItsPath() throws Exception {
    importJson(file(GCP, single("Which runs containers serverless?"), single("Second one?")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(2))
        .andExpect(jsonPath("$.drafts").value(0))
        .andExpect(jsonPath("$.skippedDuplicates").value(0))
        .andExpect(jsonPath("$.topicsCreated").value(3))
        .andExpect(jsonPath("$.problems").isEmpty());

    UUID leaf =
        jdbc.queryForObject(
            "select id from topic where owner_id = ? and name = 'Professional Cloud Developer'",
            UUID.class,
            me);
    assertThat(
            count(
                "select count(*) from question where owner_id = ? and primary_topic_id = ? and"
                    + " status = 'ACTIVE' and difficulty = 3 and explanation_md = 'Serverless.'",
                me,
                leaf))
        .isEqualTo(2);
    assertThat(
            count(
                "select count(*) from answer_option a join question q on q.id = a.question_id where q.owner_id = ? and a.is_correct",
                me))
        .isEqualTo(2);
  }

  @Test
  void reusesExistingTopicsIgnoringCaseAndAddsExtraTopics() throws Exception {
    UUID cloud =
        jdbc.queryForObject(
            "insert into topic (owner_id, name) values (?, 'Cloud') returning id", UUID.class, me);

    String withExtra =
        """
        {"type":"FLASHCARD","prompt":"What is IAM?","answer":"Access control.",
         "topics":["cloud › Security"]}
        """;
    importJson(file("  CLOUD ", withExtra))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(1))
        .andExpect(jsonPath("$.topicsCreated").value(1));

    UUID question = onlyQuestion();
    assertThat(
            jdbc.queryForObject(
                "select primary_topic_id from question where id = ?", UUID.class, question))
        .isEqualTo(cloud);
    assertThat(count("select count(*) from question_topic where question_id = ?", question))
        .isEqualTo(2);
  }

  @Test
  void importingTheSameFileAgainSkipsEveryQuestion() throws Exception {
    String body = file(GCP, single("Q one?"), single("Q two?"), unanswered("Q three?"));
    importJson(body)
        .andExpect(jsonPath("$.created").value(2))
        .andExpect(jsonPath("$.drafts").value(1));

    importJson(body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(0))
        .andExpect(jsonPath("$.drafts").value(0))
        .andExpect(jsonPath("$.skippedDuplicates").value(3))
        .andExpect(jsonPath("$.topicsCreated").value(0));
    assertThat(questionsOf(me)).isEqualTo(3);
  }

  @Test
  void duplicatesIgnoreCaseAndRepeatedSpacesWithinTheFileToo() throws Exception {
    importJson(file(GCP, single("What  is   Pub/Sub?"), single("  what is pub/sub? ")))
        .andExpect(jsonPath("$.created").value(1))
        .andExpect(jsonPath("$.skippedDuplicates").value(1));
  }

  @Test
  void duplicatesAreCheckedPerUser() throws Exception {
    importAs(other(), false, file("Theirs", single("Shared prompt?")))
        .andExpect(jsonPath("$.created").value(1));

    importJson(file(GCP, single("Shared prompt?")))
        .andExpect(jsonPath("$.created").value(1))
        .andExpect(jsonPath("$.skippedDuplicates").value(0));
  }

  @Test
  void questionsWithoutAnAnswerAreSavedAsDrafts() throws Exception {
    String flashcard = "{\"type\":\"FLASHCARD\",\"prompt\":\"What is GKE?\"}";
    String noOptions = "{\"type\":\"MULTIPLE_CHOICE\",\"prompt\":\"Pick the managed ones.\"}";
    importJson(file(GCP, unanswered("Which one?"), flashcard, noOptions))
        .andExpect(jsonPath("$.created").value(0))
        .andExpect(jsonPath("$.drafts").value(3))
        .andExpect(jsonPath("$.problems").isEmpty());
    assertThat(count("select count(*) from question where owner_id = ? and status = 'DRAFT'", me))
        .isEqualTo(3);
  }

  @Test
  void invalidQuestionsAreListedByPositionAndTheRestImported() throws Exception {
    String twoCorrect =
        """
        {"type":"SINGLE_CHOICE","prompt":"Two right?",
         "options":[{"text":"A","correct":true},{"text":"B","correct":true}]}
        """;
    String badType = "{\"type\":\"ESSAY\",\"prompt\":\"Write.\"}";
    String badDifficulty =
        "{\"type\":\"FLASHCARD\",\"prompt\":\"Hard?\",\"answer\":\"Yes\",\"difficulty\":7}";
    importJson(file(GCP, single("Good one?"), twoCorrect, badType, badDifficulty, "null"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(1))
        .andExpect(jsonPath("$.problems.length()").value(4))
        .andExpect(jsonPath("$.problems[0].index").value(2))
        .andExpect(jsonPath("$.problems[0].reason").value(QuestionRules.SINGLE_CORRECT))
        .andExpect(jsonPath("$.problems[1].index").value(3))
        .andExpect(jsonPath("$.problems[1].reason").value(QuestionRules.TYPE))
        .andExpect(jsonPath("$.problems[2].index").value(4))
        .andExpect(jsonPath("$.problems[2].reason").value(QuestionRules.DIFFICULTY))
        .andExpect(jsonPath("$.problems[3].index").value(5))
        .andExpect(jsonPath("$.problems[3].reason").value(ImportService.EMPTY));
    assertThat(questionsOf(me)).isEqualTo(1);
  }

  @Test
  void aQuestionNeedsATopicAndValidPaths() throws Exception {
    String badPath =
        "{\"type\":\"FLASHCARD\",\"prompt\":\"Bad path?\",\"answer\":\"x\",\"topics\":[\"A ›  › B\"]}";
    importJson(file(null, single("No topic?"), badPath))
        .andExpect(jsonPath("$.created").value(0))
        .andExpect(jsonPath("$.problems[0].reason").value(ImportService.NO_TOPIC))
        .andExpect(jsonPath("$.problems[1].index").value(2));
    assertThat(count("select count(*) from topic where owner_id = ?", me)).isZero();
  }

  @Test
  void aDryRunReportsWhatAnImportWouldDo() throws Exception {
    // Inside this test's transaction only the report can be checked; ImportDryRunTests checks
    // that nothing is saved.
    String body = file(GCP, single("Dry one?"), unanswered("Dry two?"), single("Dry one?"));
    importAs(me(), true, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(1))
        .andExpect(jsonPath("$.drafts").value(1))
        .andExpect(jsonPath("$.skippedDuplicates").value(1))
        .andExpect(jsonPath("$.topicsCreated").value(3));
  }

  @Test
  void anImportNeedsOneToTwoThousandQuestions() throws Exception {
    importJson(file(GCP)).andExpect(status().isBadRequest());
    importJson("{\"topic\":\"X\"}").andExpect(status().isBadRequest());

    String many =
        IntStream.range(0, ImportService.MAX_QUESTIONS + 1)
            .mapToObj(i -> "{\"type\":\"FLASHCARD\",\"prompt\":\"Q" + i + "\"}")
            .collect(Collectors.joining(","));
    importJson(file(GCP, many)).andExpect(status().isBadRequest());
    assertThat(questionsOf(me)).isZero();
  }

  @Test
  void fillingByIdCompletesADraftAndMakesItActive() throws Exception {
    importJson(file(GCP, unanswered("Fill me?")));
    UUID id = onlyQuestion();

    String filled =
        """
        {"id":"%s","options":[{"text":"A","correct":false},{"text":"B","correct":true}],
         "explanation":"Because B."}
        """
            .formatted(id);
    importJson(file(null, filled))
        .andExpect(jsonPath("$.filled").value(1))
        .andExpect(jsonPath("$.problems").isEmpty());

    assertThat(
            count(
                "select count(*) from question where id = ? and status = 'ACTIVE'"
                    + " and explanation_md = 'Because B.'",
                id))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select text_md from answer_option where question_id = ? and is_correct",
                String.class,
                id))
        .isEqualTo("B");
  }

  @Test
  void fillingNeverOverwritesAnActiveQuestion() throws Exception {
    importJson(file(GCP, single("Already answered?")));
    UUID id = onlyQuestion();

    String overwrite =
        """
        {"id":"%s","options":[{"text":"X","correct":true},{"text":"Y","correct":false}]}
        """
            .formatted(id);
    importJson(file(null, overwrite))
        .andExpect(jsonPath("$.filled").value(0))
        .andExpect(jsonPath("$.problems[0].reason").value(ImportService.ALREADY_ACTIVE));
    assertThat(
            count("select count(*) from answer_option where question_id = ? and text_md = 'X'", id))
        .isZero();
  }

  @Test
  void fillingNeedsAnAnswerAndTheUsersOwnDraft() throws Exception {
    importJson(file(GCP, unanswered("Mine?")));
    UUID id = onlyQuestion();

    String stillEmpty = "{\"id\":\"%s\",\"explanation\":\"No answer yet.\"}".formatted(id);
    importJson(file(null, stillEmpty))
        .andExpect(jsonPath("$.problems[0].reason").value(ImportService.STILL_NO_ANSWER));

    String fromOther =
        """
        {"id":"%s","options":[{"text":"A","correct":true},{"text":"B","correct":false}]}
        """
            .formatted(id);
    importAs(other(), false, file(null, fromOther))
        .andExpect(jsonPath("$.problems[0].reason").value(ImportService.NOT_FOUND));
    importJson(file(null, "{\"id\":\"%s\"}".formatted(UUID.randomUUID())))
        .andExpect(jsonPath("$.problems[0].reason").value(ImportService.NOT_FOUND));
    assertThat(count("select count(*) from question where owner_id = ? and status = 'DRAFT'", me))
        .isEqualTo(1);
  }

  @Test
  void savingACompleteEditActivatesADraft() throws Exception {
    importJson(file(GCP, "{\"type\":\"FLASHCARD\",\"prompt\":\"Draft card?\"}"));
    UUID id = onlyQuestion();
    UUID topic =
        jdbc.queryForObject(
            "select primary_topic_id from question where owner_id = ?", UUID.class, me);
    long version =
        jdbc.queryForObject("select version from question where owner_id = ?", Long.class, me);

    String edit =
        """
        {"type":"FLASHCARD","promptMd":"Draft card?","answerMd":"Now answered.","difficulty":2,
         "topicIds":["%s"],"primaryTopicId":"%s","options":[],"version":%d}
        """
            .formatted(topic, topic, version);
    mvc.perform(
            put("/api/v1/questions/" + id)
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .with(me())
                .with(csrf())
                .content(edit))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void importingNeedsSignInAndTheCsrfToken() throws Exception {
    String body = file(GCP, single("Anyone?"));
    mvc.perform(
            post("/api/v1/imports")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/imports")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .with(me())
                .content(body))
        .andExpect(status().isForbidden());
    assertThat(questionsOf(me) + questionsOf(other)).isZero();
  }

  @Test
  void exactlyTwoThousandQuestionsAreAccepted() throws Exception {
    String many =
        IntStream.range(0, ImportService.MAX_QUESTIONS)
            .mapToObj(i -> "{\"type\":\"FLASHCARD\",\"prompt\":\"Q" + i + "\",\"answer\":\"A\"}")
            .collect(Collectors.joining(","));
    importJson(file(GCP, many))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(ImportService.MAX_QUESTIONS));
    assertThat(questionsOf(me)).isEqualTo(ImportService.MAX_QUESTIONS);
  }

  @Test
  void topicNamesInAPathHaveUpToSixtyCharacters() throws Exception {
    String sixty = "a".repeat(60);
    String card = "{\"type\":\"FLASHCARD\",\"prompt\":\"%s\",\"answer\":\"x\",\"topics\":[\"%s\"]}";
    importJson(
            file(
                null,
                card.formatted("Sixty?", "Root › " + sixty),
                card.formatted("Sixty-one?", "Root › " + sixty + "b")))
        .andExpect(jsonPath("$.created").value(1))
        .andExpect(jsonPath("$.problems[0].index").value(2));
    assertThat(count("select count(*) from topic where owner_id = ? and name = ?", me, sixty))
        .isEqualTo(1);
  }

  @Test
  void difficultyDefaultsToTwo() throws Exception {
    importJson(file(GCP, "{\"type\":\"FLASHCARD\",\"prompt\":\"Default?\",\"answer\":\"x\"}"))
        .andExpect(jsonPath("$.created").value(1));
    assertThat(count("select difficulty from question where owner_id = ?", me)).isEqualTo(2);
  }

  @Test
  void aBadIdIsReportedAndAValueOfTheWrongJsonTypeRejectsTheFile() throws Exception {
    importJson(file(null, "{\"id\":\"not-a-uuid\",\"answer\":\"x\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.problems[0].reason").value(ImportService.BAD_ID));

    importJson(
            file(GCP, single("Fine?"), "{\"type\":\"FLASHCARD\",\"prompt\":\"P\",\"options\":{}}"))
        .andExpect(status().isBadRequest());
    assertThat(questionsOf(me)).isZero();
  }

  @Test
  void aFillOnlyFileCreatesNoTopics() throws Exception {
    importJson(file(GCP, unanswered("Fill only?")));
    UUID id = onlyQuestion();
    int topics = count("select count(*) from topic where owner_id = ?", me);

    String filled =
        """
        {"id":"%s","options":[{"text":"A","correct":true},{"text":"B","correct":false}]}
        """
            .formatted(id);
    importJson(file("Renamed › Elsewhere", filled))
        .andExpect(jsonPath("$.filled").value(1))
        .andExpect(jsonPath("$.topicsCreated").value(0));
    assertThat(count("select count(*) from topic where owner_id = ?", me)).isEqualTo(topics);
  }
}
