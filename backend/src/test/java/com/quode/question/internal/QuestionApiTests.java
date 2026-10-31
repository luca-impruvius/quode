package com.quode.question.internal;

import static org.assertj.core.api.Assertions.assertThat;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class QuestionApiTests {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  private UUID java;
  private UUID collections;
  private UUID theirTopic;

  @BeforeEach
  void data() {
    UUID me = user("me@example.com", "sub-me");
    UUID other = user("other@example.com", "sub-other");
    UUID languages = topic(me, null, "Languages");
    java = topic(me, languages, "Java");
    collections = topic(me, java, "Collections");
    theirTopic = topic(other, null, "Theirs");
  }

  private UUID user(String email, String sub) {
    return jdbc.queryForObject(
        "insert into app_user (email, google_sub, display_name) values (?, ?, 'U') returning id",
        UUID.class,
        email,
        sub);
  }

  private UUID topic(UUID owner, UUID parent, String name) {
    return jdbc.queryForObject(
        "insert into topic (owner_id, parent_id, name) values (?, ?, ?) returning id",
        UUID.class,
        owner,
        parent,
        name);
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

  private String flashcard(UUID... topics) {
    StringBuilder ids = new StringBuilder();
    for (UUID t : topics) {
      ids.append(ids.isEmpty() ? "" : ",").append('"').append(t).append('"');
    }
    return """
        {"type":"FLASHCARD","promptMd":"What is a HashMap?","answerMd":"A hash table.",
         "difficulty":2,"topicIds":[%s],"primaryTopicId":"%s","options":[]}
        """
        .formatted(ids, topics[0]);
  }

  private ResultActions create(RequestPostProcessor user, String body) throws Exception {
    return mvc.perform(api(post("/api/v1/questions")).with(user).with(csrf()).content(body));
  }

  private String createdId(String body) throws Exception {
    String json =
        create(me(), body)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(json, "$.id");
  }

  @Test
  void createsAndReadsAFlashcardWithTopicPaths() throws Exception {
    String id = createdId(flashcard(collections, java));

    mvc.perform(api(get("/api/v1/questions/" + id)).with(me()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("FLASHCARD"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.answerMd").value("A hash table."))
        .andExpect(jsonPath("$.topics.length()").value(2))
        .andExpect(jsonPath("$.topics[0].path").value("Languages › Java › Collections"))
        .andExpect(jsonPath("$.topics[0].primary").value(true))
        .andExpect(jsonPath("$.topics[1].name").value("Java"))
        .andExpect(jsonPath("$.topics[1].primary").value(false))
        .andExpect(jsonPath("$.archivedAt").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void createsChoiceQuestionsWithOptionsInOrder() throws Exception {
    String id =
        createdId(
            """
            {"type":"MULTIPLE_CHOICE","promptMd":"Which are thread-safe?","difficulty":3,
             "topicIds":["%s"],"primaryTopicId":"%s",
             "options":[{"textMd":"HashMap","correct":false},
                        {"textMd":"ConcurrentHashMap","correct":true},
                        {"textMd":"Vector","correct":true}]}
            """
                .formatted(java, java));

    mvc.perform(api(get("/api/v1/questions/" + id)).with(me()))
        .andExpect(jsonPath("$.options[*].textMd").exists())
        .andExpect(jsonPath("$.options[0].textMd").value("HashMap"))
        .andExpect(jsonPath("$.options[1].correct").value(true))
        .andExpect(jsonPath("$.options[2].textMd").value("Vector"));
  }

  @Test
  void createsOpenTextAndSingleChoice() throws Exception {
    createdId(
        """
        {"type":"OPEN_TEXT","promptMd":"Explain GC","answerMd":"Model answer","explanationMd":"Why",
         "difficulty":1,"sourceUrl":"https://docs.oracle.com","topicIds":["%s"],"primaryTopicId":"%s"}
        """
            .formatted(java, java));
    createdId(
        """
        {"type":"SINGLE_CHOICE","promptMd":"Pick","difficulty":2,"topicIds":["%s"],
         "primaryTopicId":"%s","options":[{"textMd":"A","correct":true},{"textMd":"B","correct":false}]}
        """
            .formatted(java, java));
  }

  @Test
  void rejectsABrokenRuleWithItsMessage() throws Exception {
    create(
            me(),
            """
            {"type":"SINGLE_CHOICE","promptMd":"Pick","difficulty":2,"topicIds":["%s"],
             "primaryTopicId":"%s","options":[{"textMd":"A","correct":true},{"textMd":"B","correct":true}]}
            """
                .formatted(java, java))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(QuestionRules.SINGLE_CORRECT));
  }

  @Test
  void anUnknownTypeIs400() throws Exception {
    create(me(), flashcard(java).replace("FLASHCARD", "ESSAY")).andExpect(status().isBadRequest());
  }

  @Test
  void anotherUsersTopicOrAnUnknownOneIs404() throws Exception {
    create(me(), flashcard(theirTopic)).andExpect(status().isNotFound());
    create(me(), flashcard(java, UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void editsWithTheVersionItReadReplacingTopicsAndOptions() throws Exception {
    String id =
        createdId(
            """
            {"type":"SINGLE_CHOICE","promptMd":"Pick","difficulty":2,"topicIds":["%s","%s"],
             "primaryTopicId":"%s","options":[{"textMd":"A","correct":true},{"textMd":"B","correct":false}]}
            """
                .formatted(java, collections, java));

    mvc.perform(
            api(put("/api/v1/questions/" + id))
                .with(me())
                .with(csrf())
                .content(
                    """
                    {"type":"SINGLE_CHOICE","promptMd":"Pick again","difficulty":3,
                     "topicIds":["%s"],"primaryTopicId":"%s","version":0,
                     "options":[{"textMd":"B","correct":false},{"textMd":"C","correct":false},
                                {"textMd":"A","correct":true}]}
                    """
                        .formatted(collections, collections)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.promptMd").value("Pick again"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.topics.length()").value(1))
        .andExpect(jsonPath("$.topics[0].name").value("Collections"))
        .andExpect(jsonPath("$.options.length()").value(3))
        .andExpect(jsonPath("$.options[2].textMd").value("A"));
    // The deferred constraints hold at commit: positions unique, primary among the topics.
    jdbc.execute("set constraints all immediate");
  }

  @Test
  void aStaleSaveIs409() throws Exception {
    String id = createdId(flashcard(java));
    String edit = flashcard(java).replace("\"options\":[]", "\"options\":[],\"version\":%s");

    mvc.perform(
            api(put("/api/v1/questions/" + id)).with(me()).with(csrf()).content(edit.formatted(0)))
        .andExpect(status().isOk());
    mvc.perform(
            api(put("/api/v1/questions/" + id)).with(me()).with(csrf()).content(edit.formatted(0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(QuestionService.STALE));
  }

  @Test
  void anEditNeedsTheVersion() throws Exception {
    String id = createdId(flashcard(java));

    mvc.perform(
            api(put("/api/v1/questions/" + id)).with(me()).with(csrf()).content(flashcard(java)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(QuestionService.VERSION_REQUIRED));
  }

  @Test
  void archivesAndUnarchivesWithTheVersion() throws Exception {
    String id = createdId(flashcard(java));

    mvc.perform(
            api(post("/api/v1/questions/" + id + "/archive"))
                .with(me())
                .with(csrf())
                .content("{\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").isNotEmpty())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(
            api(post("/api/v1/questions/" + id + "/unarchive"))
                .with(me())
                .with(csrf())
                .content("{\"version\":0}"))
        .andExpect(status().isConflict());
    mvc.perform(
            api(post("/api/v1/questions/" + id + "/unarchive"))
                .with(me())
                .with(csrf())
                .content("{\"version\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void anotherUsersQuestionIsNotFoundOnEveryPath() throws Exception {
    String id = createdId(flashcard(java));

    mvc.perform(api(get("/api/v1/questions/" + id)).with(other())).andExpect(status().isNotFound());
    mvc.perform(
            api(put("/api/v1/questions/" + id))
                .with(other())
                .with(csrf())
                .content(
                    flashcard(theirTopic)
                        .replace("\"options\":[]", "\"options\":[],\"version\":0")))
        .andExpect(status().isNotFound());
    for (String action : new String[] {"archive", "unarchive"}) {
      mvc.perform(
              api(post("/api/v1/questions/" + id + "/" + action))
                  .with(other())
                  .with(csrf())
                  .content("{\"version\":0}"))
          .andExpect(status().isNotFound());
    }
  }

  @Test
  void writesNeedTheCsrfTokenAndASignIn() throws Exception {
    mvc.perform(api(post("/api/v1/questions")).with(me()).content(flashcard(java)))
        .andExpect(status().isForbidden());
    mvc.perform(api(get("/api/v1/questions/" + UUID.randomUUID())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void theDatabaseKeepsThePrimaryTopicAmongTheQuestionsTopics() throws Exception {
    String id = createdId(flashcard(java, collections));

    jdbc.update(
        "delete from question_topic where question_id = ?::uuid and topic_id = ?", id, java);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> jdbc.execute("set constraints all immediate"))
        .hasMessageContaining("question_primary_is_linked_fkey");
  }

  @Test
  void theDatabaseRefusesATopicOfAnotherUser() throws Exception {
    String id = createdId(flashcard(java));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into question_topic (question_id, topic_id, owner_id)"
                        + " select q.id, ?, q.owner_id from question q where q.id = ?::uuid",
                    theirTopic,
                    id))
        .hasMessageContaining("question_topic_topic_same_owner_fkey");
  }

  private static final String CHOICE =
      """
      {"type":"SINGLE_CHOICE","promptMd":"Pick","difficulty":2,"topicIds":[%s],
       "primaryTopicId":"%s","version":%s,"options":[%s]}
      """;

  private static String quoted(UUID... ids) {
    StringBuilder out = new StringBuilder();
    for (UUID id : ids) {
      out.append(out.isEmpty() ? "" : ",").append('"').append(id).append('"');
    }
    return out.toString();
  }

  private String choice(UUID primary, String topics, Object version, String options) {
    return CHOICE.formatted(topics, primary, version, options);
  }

  private ResultActions edit(String id, String body) throws Exception {
    return mvc.perform(api(put("/api/v1/questions/" + id)).with(me()).with(csrf()).content(body));
  }

  @Test
  void anEditOfOnlyTheOptionsBumpsTheVersion() throws Exception {
    String ab = "{\"textMd\":\"A\",\"correct\":true},{\"textMd\":\"B\",\"correct\":false}";
    String ba = "{\"textMd\":\"A\",\"correct\":false},{\"textMd\":\"B\",\"correct\":true}";
    String id = createdId(choice(java, quoted(java), null, ab));

    edit(id, choice(java, quoted(java), 0, ba))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    // A second client still holding version 0 can't overwrite that edit.
    edit(id, choice(java, quoted(java), 0, ab)).andExpect(status().isConflict());
  }

  @Test
  void anEditOfOnlyTheTopicsBumpsTheVersion() throws Exception {
    String ab = "{\"textMd\":\"A\",\"correct\":true},{\"textMd\":\"B\",\"correct\":false}";
    String id = createdId(choice(java, quoted(java), null, ab));

    edit(id, choice(java, quoted(java, collections), 0, ab))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    edit(id, choice(java, quoted(java), 0, ab)).andExpect(status().isConflict());
  }

  @Test
  void optionIdsStayTheSameWhenTheyKeepTheirPlace() throws Exception {
    String ab = "{\"textMd\":\"A\",\"correct\":true},{\"textMd\":\"B\",\"correct\":false}";
    String abc = ab + ",{\"textMd\":\"C\",\"correct\":false}";
    String id = createdId(choice(java, quoted(java), null, ab));
    String before =
        mvc.perform(api(get("/api/v1/questions/" + id)).with(me()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String after =
        edit(id, choice(java, quoted(java), 0, abc))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat((String) JsonPath.read(after, "$.options[0].id"))
        .isEqualTo(JsonPath.read(before, "$.options[0].id"));
    assertThat((String) JsonPath.read(after, "$.options[1].id"))
        .isEqualTo(JsonPath.read(before, "$.options[1].id"));
    assertThat((String) JsonPath.read(after, "$.options[2].textMd")).isEqualTo("C");
  }

  @Test
  void aJavascriptSourceLinkIs400() throws Exception {
    create(
            me(),
            flashcard(java)
                .replace("\"options\":[]", "\"options\":[],\"sourceUrl\":\"javascript:alert(1)\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(QuestionRules.SOURCE));
  }
}
