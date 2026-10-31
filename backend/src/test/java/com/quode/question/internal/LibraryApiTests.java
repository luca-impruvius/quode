package com.quode.question.internal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quode.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** GET /api/v1/questions: the Library list (docs/specs/M1-library.md). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class LibraryApiTests {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  private UUID me;
  private UUID java;
  private UUID collections;
  private UUID web;
  private UUID theirTopic;

  @BeforeEach
  void data() {
    me = user("me@example.com", "sub-me");
    UUID other = user("other@example.com", "sub-other");
    UUID languages = topic(me, null, "Languages");
    java = topic(me, languages, "Java");
    collections = topic(me, java, "Collections");
    web = topic(me, null, "Web");
    theirTopic = topic(other, null, "Theirs");

    // Created in this order, a second apart, so "newest first" is predictable.
    question(me, "How does HashMap handle collisions?", "Chaining", "FLASHCARD", 2, collections, 1);
    question(me, "What is 100% CPU?", "A busy core", "OPEN_TEXT", 3, java, 2);
    question(me, "What is CORS?", "Cross-origin rules", "FLASHCARD", 1, web, 3);
    UUID archived = question(me, "Old Java question", "Gone", "FLASHCARD", 1, java, 4);
    jdbc.update("update question set archived_at = now() where id = ?", archived);
    UUID flagged = question(me, "Fix this Java one", "Typo", "FLASHCARD", 2, java, 5);
    jdbc.update("update question set flagged_at = now() where id = ?", flagged);
    question(other, "Their HashMap question", "Secret", "FLASHCARD", 2, theirTopic, 6);
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

  private UUID question(
      UUID owner, String prompt, String answer, String type, int difficulty, UUID topic, int age) {
    UUID id =
        jdbc.queryForObject(
            """
            insert into question (owner_id, type, prompt_md, answer_md, difficulty, primary_topic_id,
                                  created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, now() - make_interval(secs => ?), now() - make_interval(secs => ?))
            returning id
            """,
            UUID.class,
            owner,
            type,
            prompt,
            answer,
            difficulty,
            topic,
            100 - age,
            100 - age);
    jdbc.update(
        "insert into question_topic (question_id, topic_id, owner_id) values (?, ?, ?)",
        id,
        topic,
        owner);
    return id;
  }

  private static RequestPostProcessor signedIn() {
    return oidcLogin().idToken(token -> token.subject("sub-me"));
  }

  private ResultActions list(String query) throws Exception {
    return mvc.perform(get("/api/v1/questions" + query).contextPath("/api").with(signedIn()));
  }

  @Test
  void listsMyActiveQuestionsNewestFirstWithTheirPrimaryTopic() throws Exception {
    list("")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.items.length()").value(4))
        .andExpect(jsonPath("$.items[0].promptMd").value("Fix this Java one"))
        .andExpect(jsonPath("$.items[0].flagged").value(true))
        .andExpect(jsonPath("$.items[3].promptMd").value("How does HashMap handle collisions?"))
        .andExpect(jsonPath("$.items[3].primaryTopic.name").value("Collections"))
        .andExpect(jsonPath("$.items[3].primaryTopic.path").value("Languages › Java › Collections"))
        .andExpect(jsonPath("$.items[3].type").value("FLASHCARD"));
  }

  @Test
  void aTopicFilterIncludesItsSubTopics() throws Exception {
    list("?topicId=" + java)
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(
            jsonPath("$.items[*].promptMd")
                .value(org.hamcrest.Matchers.hasItem("How does HashMap handle collisions?")));
    list("?topicId=" + collections).andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void filtersByTypeDifficultyAndStatus() throws Exception {
    list("?type=OPEN_TEXT")
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].promptMd").value("What is 100% CPU?"));
    list("?difficulty=1")
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].promptMd").value("What is CORS?"));
    list("?status=ARCHIVED")
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].promptMd").value("Old Java question"))
        .andExpect(jsonPath("$.items[0].archived").value(true));
    list("?status=FLAGGED")
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].promptMd").value("Fix this Java one"));
  }

  @Test
  void searchesPromptAndAnswerIgnoringCase() throws Exception {
    list("?q=hashmap").andExpect(jsonPath("$.totalElements").value(1));
    list("?q=CROSS-ORIGIN")
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].promptMd").value("What is CORS?"));
  }

  @Test
  void searchTreatsPercentAndUnderscoreLiterally() throws Exception {
    search("100%").andExpect(jsonPath("$.totalElements").value(1));
    search("%").andExpect(jsonPath("$.totalElements").value(1));
    search("_").andExpect(jsonPath("$.totalElements").value(0));
  }

  // A request parameter, so the text arrives exactly as typed (a URL template would re-encode %).
  private ResultActions search(String text) throws Exception {
    return mvc.perform(
        get("/api/v1/questions").contextPath("/api").queryParam("q", text).with(signedIn()));
  }

  @Test
  void sortsByDifficultyOrUpdated() throws Exception {
    list("?sort=difficulty")
        .andExpect(jsonPath("$.items[0].difficulty").value(1))
        .andExpect(jsonPath("$.items[3].difficulty").value(3));
    list("?sort=updated").andExpect(jsonPath("$.items[0].promptMd").value("Fix this Java one"));
  }

  @Test
  void pagesWithATotal() throws Exception {
    list("?size=3&page=0")
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.hasNext").value(true));
    list("?size=3&page=1")
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void neverShowsAnotherUsersQuestions() throws Exception {
    list("?q=Their").andExpect(jsonPath("$.totalElements").value(0));
    list("?topicId=" + theirTopic).andExpect(status().isNotFound());
  }

  @Test
  void rejectsBadParameters() throws Exception {
    for (String bad :
        List.of(
            "?size=0", "?size=51", "?page=-1", "?difficulty=4", "?sort=random", "?status=GONE")) {
      list(bad).andExpect(status().isBadRequest());
    }
  }

  @Test
  void signedOutIs401() throws Exception {
    mvc.perform(get("/api/v1/questions").contextPath("/api")).andExpect(status().isUnauthorized());
  }
}
