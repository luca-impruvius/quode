package com.quode.review.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.quode.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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

/**
 * The daily review queue (docs/specs/M3-daily-review.md, M3-01): enrolment, the queue and its
 * limits, review sessions, the quiz rule (ADR-0012) and the question review controls. Each test
 * runs in one rolled-back transaction, so the database's now() is the same throughout a test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ReviewApiTests {

  private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  private UUID me;
  private UUID other;

  @BeforeEach
  void data() throws Exception {
    me = user("me@example.com", "sub-me");
    other = user("other@example.com", "sub-other");
    StringBuilder cards = new StringBuilder();
    for (int i = 1; i <= 8; i++) {
      cards.append(
          "{\"type\":\"FLASHCARD\",\"prompt\":\"Q%d?\",\"answer\":\"A%d\"},".formatted(i, i));
    }
    String file =
        """
        {"topic":"Cloud","questions":[%s
          {"type":"SINGLE_CHOICE","prompt":"Serverless containers?",
           "options":[{"text":"GKE","correct":false},{"text":"Cloud Run","correct":true}]},
          {"type":"FLASHCARD","prompt":"Draft?"},
          {"type":"FLASHCARD","prompt":"Archived?","answer":"x"}]}
        """
            .formatted(cards);
    send(post("/api/v1/imports"), me(), file).andExpect(jsonPath("$.created").value(10));
    jdbc.update("update question set archived_at = now() where prompt_md = 'Archived?'");
    // Enrolment order: Q1 oldest, then Q2 … Q8, then the choice question.
    jdbc.update(
        "update review_state rs set enrolled_at = now() - make_interval(mins => 100 - coalesce("
            + "nullif(regexp_replace(q.prompt_md, '\\D', '', 'g'), '')::int, 50))"
            + " from question q where q.id = rs.question_id");
  }

  // --- helpers ---

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

  private ResultActions read(String path, RequestPostProcessor user) throws Exception {
    return mvc.perform(get("/api/v1" + path).contextPath("/api").with(user));
  }

  private UUID question(String prompt) {
    return jdbc.queryForObject(
        "select id from question where prompt_md = ? and owner_id = ?", UUID.class, prompt, me);
  }

  private ResultActions startReview(String extra) throws Exception {
    return send(
        post("/api/v1/study/sessions"), me(), "{\"kind\":\"REVIEW\",\"extra\":\"" + extra + "\"}");
  }

  private String newReview(String extra) throws Exception {
    String json =
        startReview(extra)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("REVIEW"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(json, "$.id");
  }

  private List<String> order(String session) {
    return jdbc.queryForList(
        "select q.prompt_md from study_session s"
            + " cross join lateral unnest(s.question_order) with ordinality as o(qid, n)"
            + " join question q on q.id = o.qid where s.id = ?::uuid order by o.n",
        String.class,
        session);
  }

  private ResultActions answer(String session, String prompt, String grade) throws Exception {
    return send(
        post("/api/v1/study/sessions/" + session + "/answers"),
        me(),
        "{\"questionId\":\"%s\",\"grade\":\"%s\"}".formatted(question(prompt), grade));
  }

  // Answers every remaining question of a session with the same self-grade.
  private void answerAll(String session, String grade) throws Exception {
    for (String prompt : order(session)) {
      if (!answered(session, prompt)) {
        answer(session, prompt, grade).andExpect(status().isOk());
      }
    }
  }

  private boolean answered(String session, String prompt) {
    return jdbc.queryForObject(
        "select exists (select 1 from attempt where session_id = ?::uuid and question_id = ?)",
        Boolean.class,
        session,
        question(prompt));
  }

  private String quiz(String... prompts) throws Exception {
    StringBuilder ids = new StringBuilder();
    for (String p : prompts) {
      ids.append(ids.isEmpty() ? "" : ",").append('"').append(question(p)).append('"');
    }
    String json =
        send(post("/api/v1/study/sessions"), me(), "{\"questionIds\":[" + ids + "]}")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(json, "$.id");
  }

  // The database's now(): what answered_at and started_at get in this test's transaction.
  private ReviewDays today() {
    return ReviewDays.of(
        jdbc.queryForObject("select now()", Timestamp.class).toInstant(), BUCHAREST);
  }

  /** Makes a question scheduled (reviewed before today), due at {@code dueAt}. */
  private void scheduled(String prompt, Instant dueAt) {
    jdbc.update(
        "update review_state set introduced_at = now() - interval '10 days',"
            + " last_reviewed_at = now() - interval '5 days', due_at = ?, interval_days = 3,"
            + " reps = 2, ease = 2.5 where question_id = ?",
        Timestamp.from(dueAt),
        question(prompt));
  }

  private void dueDaysAgo(String prompt, int days) {
    scheduled(prompt, today().startOfToday().minusSeconds(86_400L * days));
  }

  private Map<String, Object> state(String prompt) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "select * from review_state where owner_id = ? and question_id = ?",
            me,
            question(prompt));
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private void limits(int newPerDay, int reviewsPerDay) {
    jdbc.update(
        "update app_user set new_per_day = ?, reviews_per_day = ? where id = ?",
        newPerDay,
        reviewsPerDay,
        me);
  }

  // --- enrolment ---

  @Test
  void everyImportedQuestionIsEnrolledAsNewDraftsAndArchivedOnesIncluded() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from review_state where owner_id = ? and due_at is null"
                    + " and last_reviewed_at is null and introduced_at is null and reps = 0"
                    + " and ease = 2.5 and not learn_next",
                Integer.class,
                me))
        .isEqualTo(11);
  }

  @Test
  void aQuestionCreatedInTheEditorIsEnrolled() throws Exception {
    UUID topic =
        jdbc.queryForObject(
            "select id from topic where owner_id = ? and name = 'Cloud'", UUID.class, me);
    send(
            post("/api/v1/questions"),
            me(),
            """
            {"type":"FLASHCARD","promptMd":"Typed in?","answerMd":"Yes","difficulty":2,
             "topicIds":["%s"],"primaryTopicId":"%s","options":[]}
            """
                .formatted(topic, topic))
        .andExpect(status().isCreated());
    assertThat(state("Typed in?")).isNotNull().containsEntry("due_at", null);
  }

  // --- today and the queue ---

  @Test
  void todayOffersUpToFiveNewQuestionsAtFirst() throws Exception {
    read("/review/today", me())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.due").value(0))
        .andExpect(jsonPath("$.newWaiting").value(5))
        .andExpect(jsonPath("$.moreDue").value(0))
        // 9 studyable new questions; the draft and the archived one don't count.
        .andExpect(jsonPath("$.moreNew").value(4))
        .andExpect(jsonPath("$.doneToday").value(0))
        .andExpect(jsonPath("$.newToday").value(0))
        .andExpect(jsonPath("$.nextDueAt").value(nullValue()))
        .andExpect(jsonPath("$.sessionId").value(nullValue()));
  }

  @Test
  void aChangedLimitAppliesToTodaysRemainingCountAtOnce() throws Exception {
    limits(2, 30);
    answerAll(newReview("NONE"), "GOT_IT");
    read("/review/today", me()).andExpect(jsonPath("$.newWaiting").value(0));

    send(put("/api/v1/me/settings"), me(), "{\"newPerDay\":7,\"reviewsPerDay\":30}")
        .andExpect(status().isOk());
    read("/review/today", me()).andExpect(jsonPath("$.newWaiting").value(5));
  }

  @Test
  void learnMorePassesTheNewLimitAndWhatIsLeftNeverGoesNegative() throws Exception {
    limits(2, 30);
    answerAll(newReview("NONE"), "GOT_IT");

    String more = newReview("LEARN_MORE");
    assertThat(order(more)).containsExactly("Q3?", "Q4?", "Q5?", "Q6?", "Q7?");
    answerAll(more, "GOT_IT");

    read("/review/today", me())
        .andExpect(jsonPath("$.newToday").value(7))
        .andExpect(jsonPath("$.newWaiting").value(0))
        .andExpect(jsonPath("$.moreNew").value(2));
  }

  @Test
  void aReviewAsksDueQuestionsMostOverdueFirstWithNewOnesMixedIn() throws Exception {
    dueDaysAgo("Q8?", 1);
    dueDaysAgo("Q7?", 3);
    dueDaysAgo("Q6?", 2);
    limits(2, 30);

    String session = newReview("NONE");

    List<String> asked = order(session);
    assertThat(asked).hasSize(5);
    assertThat(asked.stream().filter(p -> p.matches("Q[678]\\?")))
        .containsExactly("Q7?", "Q6?", "Q8?");
    assertThat(asked.stream().filter(p -> !p.matches("Q[678]\\?"))).containsExactly("Q1?", "Q2?");
    // New ones are spread through the session, not all at the end.
    assertThat(asked.getLast()).isNotIn("Q1?", "Q2?");
  }

  @Test
  void learnNextQuestionsComeFirstAmongTheNewOnes() throws Exception {
    send(
            put("/api/v1/questions/" + question("Q5?") + "/review"),
            me(),
            "{\"enrolled\":true,\"learnNext\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.learnNext").value(true));
    limits(2, 30);

    assertThat(order(newReview("NONE"))).containsExactly("Q5?", "Q1?");
  }

  @Test
  void draftsAndArchivedQuestionsNeverEnterTheQueue() throws Exception {
    scheduled("Draft?", today().startOfToday());
    scheduled("Archived?", today().startOfToday());
    limits(100, 30);

    assertThat(order(newReview("NONE"))).doesNotContain("Draft?", "Archived?").hasSize(9);
  }

  @Test
  void aQuestionDueAtTheStartOfTomorrowIsNotDueToday() throws Exception {
    limits(0, 30);
    scheduled("Q1?", today().startOfTomorrow());
    scheduled("Q2?", today().startOfTomorrow().minusMillis(1));

    read("/review/today", me())
        .andExpect(jsonPath("$.due").value(1))
        .andExpect(jsonPath("$.nextDueAt").value(today().startOfTomorrow().toString()));
    assertThat(order(newReview("NONE"))).containsExactly("Q2?");
  }

  @Test
  void reviewAnswersMoveTheScheduleAndCountForToday() throws Exception {
    limits(2, 30);
    String session = newReview("NONE");
    answer(session, "Q1?", "GOT_IT").andExpect(jsonPath("$.outcome").value("CORRECT"));
    answer(session, "Q2?", "MISSED");

    Map<String, Object> got = state("Q1?");
    assertThat(((Timestamp) got.get("due_at")).toInstant()).isEqualTo(today().dueAt(1));
    assertThat(got.get("introduced_at")).isNotNull().isEqualTo(got.get("last_reviewed_at"));
    assertThat(got).containsEntry("reps", 1).containsEntry("interval_days", 1);
    assertThat(state("Q2?")).containsEntry("lapses", 1).containsEntry("reps", 0);

    read("/review/today", me())
        .andExpect(jsonPath("$.newToday").value(2))
        .andExpect(jsonPath("$.doneToday").value(0))
        .andExpect(jsonPath("$.newWaiting").value(0))
        .andExpect(jsonPath("$.moreNew").value(7))
        .andExpect(jsonPath("$.nextDueAt").value(today().dueAt(1).toString()));
  }

  @Test
  void choiceQuestionsInAReviewAreGradedAutomatically() throws Exception {
    jdbc.update(
        "update review_state set learn_next = true where question_id = ?",
        question("Serverless containers?"));
    limits(1, 30);
    String session = newReview("NONE");
    UUID gke =
        jdbc.queryForObject(
            "select id from answer_option where text_md = 'GKE' and question_id = ?",
            UUID.class,
            question("Serverless containers?"));
    send(
            post("/api/v1/study/sessions/" + session + "/answers"),
            me(),
            "{\"questionId\":\"%s\",\"selectedOptionIds\":[\"%s\"]}"
                .formatted(question("Serverless containers?"), gke))
        .andExpect(jsonPath("$.outcome").value("WRONG"));
    assertThat(state("Serverless containers?")).containsEntry("lapses", 1);
  }

  @Test
  void limitsCountAcrossSessionsInOneDay() throws Exception {
    limits(2, 30);
    answerAll(newReview("NONE"), "GOT_IT");

    // Both new ones were used up today: nothing is due, so there's nothing to start.
    startReview("NONE").andExpect(status().isConflict());
    read("/review/today", me()).andExpect(jsonPath("$.newToday").value(2));
  }

  @Test
  void theReviewLimitCountsReviewsDoneTodayAndKeepGoingContinuesPastIt() throws Exception {
    limits(0, 2);
    for (int i = 1; i <= 5; i++) {
      dueDaysAgo("Q" + i + "?", i);
    }

    String first = newReview("NONE");
    assertThat(order(first)).containsExactly("Q5?", "Q4?");
    answerAll(first, "GOT_IT");

    read("/review/today", me())
        .andExpect(jsonPath("$.doneToday").value(2))
        .andExpect(jsonPath("$.due").value(0))
        .andExpect(jsonPath("$.moreDue").value(3));
    startReview("NONE").andExpect(status().isConflict());
    // Up to one more review limit's worth, most overdue first.
    assertThat(order(newReview("KEEP_GOING"))).containsExactly("Q3?", "Q2?");
  }

  @Test
  void aQuestionIntroducedTodayCountsOnlyAsNew() throws Exception {
    limits(1, 1);
    String session = newReview("NONE");
    answer(session, "Q1?", "MISSED");
    // Due again tomorrow, so not due today; a second same-day answer still isn't a review.
    jdbc.update(
        "update review_state set due_at = ? where question_id = ?",
        Timestamp.from(today().startOfToday()),
        question("Q1?"));
    answer(newReview("KEEP_GOING"), "Q1?", "GOT_IT");

    read("/review/today", me())
        .andExpect(jsonPath("$.newToday").value(1))
        .andExpect(jsonPath("$.doneToday").value(0));
  }

  @Test
  void backlogProtectionPausesNewQuestionsButLearnMoreStillAddsFive() throws Exception {
    limits(5, 1);
    dueDaysAgo("Q6?", 1);
    dueDaysAgo("Q7?", 1);
    dueDaysAgo("Q8?", 1);

    read("/review/today", me())
        .andExpect(jsonPath("$.due").value(1))
        .andExpect(jsonPath("$.newWaiting").value(0));
    String review = newReview("NONE");
    assertThat(order(review)).hasSize(1);
    answerAll(review, "GOT_IT");

    assertThat(order(newReview("LEARN_MORE"))).containsExactly("Q1?", "Q2?", "Q3?", "Q4?", "Q5?");
  }

  @Test
  void startingWhileTodaysReviewIsUnfinishedReturnsIt() throws Exception {
    limits(3, 30);
    String session = newReview("NONE");
    answer(session, "Q1?", "GOT_IT");

    read("/review/today", me()).andExpect(jsonPath("$.sessionId").value(session));
    startReview("LEARN_MORE")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(session))
        .andExpect(jsonPath("$.answered").value(1))
        .andExpect(jsonPath("$.current.promptMd").value(order(session).get(1)));
  }

  @Test
  void anUnfinishedReviewFromAnEarlierDayIsLeftBehind() throws Exception {
    limits(2, 30);
    String old = newReview("NONE");
    jdbc.update(
        "update study_session set started_at = ? where id = ?::uuid",
        Timestamp.from(today().startOfToday().minusSeconds(60)),
        old);

    read("/review/today", me()).andExpect(jsonPath("$.sessionId").value(nullValue()));
    assertThat(newReview("NONE")).isNotEqualTo(old);
  }

  @Test
  void aReviewStartIsCheckedLikeAnyOther() throws Exception {
    startReview("MORE").andExpect(status().isBadRequest());
    send(
            post("/api/v1/study/sessions"),
            me(),
            "{\"kind\":\"REVIEW\",\"topicIds\":[\"" + UUID.randomUUID() + "\"]}")
        .andExpect(status().isBadRequest());
    send(post("/api/v1/study/sessions"), me(), "{\"kind\":\"REVIEW\"}")
        .andExpect(status().isCreated());
  }

  @Test
  void anotherUsersQueueIsTheirOwn() throws Exception {
    read("/review/today", other())
        .andExpect(jsonPath("$.newWaiting").value(0))
        .andExpect(jsonPath("$.moreNew").value(0));
    send(post("/api/v1/study/sessions"), other(), "{\"kind\":\"REVIEW\",\"extra\":\"NONE\"}")
        .andExpect(status().isConflict());
    read("/questions/" + question("Q1?") + "/review", other()).andExpect(status().isNotFound());
  }

  // --- quizzes and the schedule (ADR-0012) ---

  @Test
  void aQuizLeavesANewQuestionNew() throws Exception {
    String quiz = quiz("Q1?");
    answer(quiz, "Q1?", "MISSED").andExpect(status().isOk());

    assertThat(state("Q1?"))
        .containsEntry("due_at", null)
        .containsEntry("introduced_at", null)
        .containsEntry("lapses", 0);
    read("/review/today", me()).andExpect(jsonPath("$.newToday").value(0));
  }

  @Test
  void aMissedQuizAnswerReschedulesButIsNotAReview() throws Exception {
    scheduled("Q1?", today().startOfToday().plusSeconds(86_400L * 5));
    Object reviewed = state("Q1?").get("last_reviewed_at");

    answer(quiz("Q1?"), "Q1?", "MISSED").andExpect(status().isOk());

    Map<String, Object> after = state("Q1?");
    assertThat(((Timestamp) after.get("due_at")).toInstant()).isEqualTo(today().dueAt(1));
    assertThat(after).containsEntry("lapses", 1).containsEntry("last_reviewed_at", reviewed);
    read("/review/today", me()).andExpect(jsonPath("$.doneToday").value(0));
  }

  @Test
  void aPartialQuizAnswerReschedulesAQuestionNotDueYet() throws Exception {
    scheduled("Q1?", today().startOfToday().plusSeconds(86_400L * 5));

    answer(quiz("Q1?"), "Q1?", "PARTIAL").andExpect(status().isOk());

    // 3 days × 1.2 = 3.6 → 4 days; ease 2.5 − 0.15.
    Map<String, Object> after = state("Q1?");
    assertThat(((Timestamp) after.get("due_at")).toInstant()).isEqualTo(today().dueAt(4));
    assertThat(((java.math.BigDecimal) after.get("ease")).doubleValue()).isEqualTo(2.35);
    assertThat(after).containsEntry("reps", 2).containsEntry("lapses", 0);
  }

  @Test
  void aGotItQuizAnswerUpdatesOnlyADueQuestion() throws Exception {
    Instant later = today().startOfToday().plusSeconds(86_400L * 5);
    scheduled("Q1?", later);
    dueDaysAgo("Q2?", 1);

    String quiz = quiz("Q1?", "Q2?");
    answer(quiz, "Q1?", "GOT_IT").andExpect(status().isOk());
    answer(quiz, "Q2?", "GOT_IT").andExpect(status().isOk());

    assertThat(((Timestamp) state("Q1?").get("due_at")).toInstant()).isEqualTo(later);
    assertThat(state("Q1?")).containsEntry("reps", 2);
    // Third Got it: 3 days × the new ease 2.55 = 7.65 → 8 days.
    assertThat(((Timestamp) state("Q2?").get("due_at")).toInstant()).isEqualTo(today().dueAt(8));
    assertThat(state("Q2?")).containsEntry("reps", 3);
  }

  @Test
  void anAnswerAfterRemoveFromReviewChangesNothing() throws Exception {
    limits(1, 30);
    String session = newReview("NONE");
    send(
            put("/api/v1/questions/" + question("Q1?") + "/review"),
            me(),
            "{\"enrolled\":false,\"learnNext\":false}")
        .andExpect(status().isOk());

    answer(session, "Q1?", "GOT_IT").andExpect(status().isOk());

    assertThat(state("Q1?")).isNull();
  }

  // --- the question's review controls ---

  @Test
  void aQuestionShowsItsReviewState() throws Exception {
    read("/questions/" + question("Q1?") + "/review", me())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrolled").value(true))
        .andExpect(jsonPath("$.learnNext").value(false))
        .andExpect(jsonPath("$.isNew").value(true))
        .andExpect(jsonPath("$.dueAt").value(nullValue()));

    scheduled("Q2?", today().dueAt(3));
    read("/questions/" + question("Q2?") + "/review", me())
        .andExpect(jsonPath("$.isNew").value(false))
        .andExpect(jsonPath("$.dueAt").value(today().dueAt(3).toString()));
  }

  @Test
  void removeFromReviewKeepsItOutOfTodayButInQuizzes() throws Exception {
    limits(1, 30);
    String path = "/api/v1/questions/" + question("Q1?") + "/review";
    send(put(path), me(), "{\"enrolled\":false,\"learnNext\":true}")
        .andExpect(jsonPath("$.enrolled").value(false))
        .andExpect(jsonPath("$.learnNext").value(false))
        .andExpect(jsonPath("$.isNew").value(false));

    assertThat(order(newReview("NONE"))).containsExactly("Q2?");
    quiz("Q1?");

    send(put(path), me(), "{\"enrolled\":true,\"learnNext\":false}")
        .andExpect(jsonPath("$.enrolled").value(true))
        .andExpect(jsonPath("$.isNew").value(true));
  }

  @Test
  void questionReviewControlsAreScopedAndChecked() throws Exception {
    String path = "/api/v1/questions/" + question("Q1?") + "/review";
    send(put(path), other(), "{\"enrolled\":true,\"learnNext\":true}")
        .andExpect(status().isNotFound());
    read("/questions/" + UUID.randomUUID() + "/review", me()).andExpect(status().isNotFound());
    send(put(path), me(), "{\"learnNext\":true}").andExpect(status().isBadRequest());
    assertThat(state("Q1?")).containsEntry("learn_next", false);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from review_state where owner_id = ?", Integer.class, other))
        .isZero();
  }
}
