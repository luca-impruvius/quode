package com.quode.taxonomy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.quode.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TopicApiTests {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void users() {
    jdbc.update(
        "insert into app_user (email, google_sub, display_name) values"
            + " ('me@example.com', 'sub-me', 'Me'), ('other@example.com', 'sub-other', 'Other')");
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

  private String create(RequestPostProcessor user, String name, String parentId) throws Exception {
    String body =
        parentId == null
            ? "{\"name\":\"%s\"}".formatted(name)
            : "{\"name\":\"%s\",\"parentId\":\"%s\"}".formatted(name, parentId);
    String json =
        mvc.perform(api(post("/api/v1/topics")).with(user).with(csrf()).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(json, "$.id");
  }

  @Test
  void createsAndListsTheTreeParentsFirstSiblingsAlphabetically() throws Exception {
    String java = create(me(), "Java", null);
    create(me(), "streams", java);
    create(me(), "Collections", java);
    create(me(), "Gradle", null);

    mvc.perform(api(get("/api/v1/topics")).with(me()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Gradle"))
        .andExpect(jsonPath("$[1].name").value("Java"))
        .andExpect(jsonPath("$[2].name").value("Collections"))
        .andExpect(jsonPath("$[3].name").value("streams"))
        .andExpect(jsonPath("$[2].parentId").value(java))
        .andExpect(jsonPath("$[0].questionCount").value(0));
  }

  @Test
  void trimsNames() throws Exception {
    create(me(), "  Java  ", null);

    mvc.perform(api(get("/api/v1/topics")).with(me()))
        .andExpect(jsonPath("$[0].name").value("Java"));
  }

  @Test
  void siblingNamesAreUniqueIgnoringCaseTopLevelIncluded() throws Exception {
    create(me(), "Java", null);

    mvc.perform(api(post("/api/v1/topics")).with(me()).with(csrf()).content("{\"name\":\"java\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(TopicService.DUPLICATE_NAME));
  }

  @Test
  void theSameNameIsFineUnderDifferentParentsAndForAnotherUser() throws Exception {
    String a = create(me(), "A", null);
    String b = create(me(), "B", null);
    create(me(), "Basics", a);
    create(me(), "Basics", b);
    create(other(), "A", null);
  }

  @Test
  void renameKeepsSiblingNamesUnique() throws Exception {
    create(me(), "Java", null);
    String kotlin = create(me(), "Kotlin", null);

    mvc.perform(
            api(put("/api/v1/topics/" + kotlin + "/name"))
                .with(me())
                .with(csrf())
                .content("{\"name\":\"JAVA\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            api(put("/api/v1/topics/" + kotlin + "/name"))
                .with(me())
                .with(csrf())
                .content("{\"name\":\"Kotlin & JVM\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Kotlin & JVM"));
  }

  @Test
  void rejectsBlankAndTooLongNames() throws Exception {
    mvc.perform(api(post("/api/v1/topics")).with(me()).with(csrf()).content("{\"name\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(TopicService.BAD_NAME));
    mvc.perform(
            api(post("/api/v1/topics"))
                .with(me())
                .with(csrf())
                .content("{\"name\":\"%s\"}".formatted("x".repeat(61))))
        .andExpect(status().isBadRequest());
    create(me(), "x".repeat(60), null);
  }

  @Test
  void movesUnderAnotherTopicAndBackToTheTop() throws Exception {
    String java = create(me(), "Java", null);
    String jvm = create(me(), "JVM", null);

    mvc.perform(
            api(put("/api/v1/topics/" + jvm + "/parent"))
                .with(me())
                .with(csrf())
                .content("{\"parentId\":\"%s\"}".formatted(java)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parentId").value(java));
    mvc.perform(
            api(put("/api/v1/topics/" + jvm + "/parent"))
                .with(me())
                .with(csrf())
                .content("{\"parentId\":null}"))
        .andExpect(status().isOk())
        // Top-level topics carry an explicit null: one stable shape for the frontend.
        .andExpect(jsonPath("$").value(hasKey("parentId")))
        .andExpect(jsonPath("$.parentId").value(nullValue()));
  }

  @Test
  void aMoveNeedsTheParentIdKey() throws Exception {
    String jvm = create(me(), "JVM", null);

    for (String body :
        new String[] {
          "{}", "{\"parentID\":null}", "{\"parentId\":\"not-a-uuid\"}", "{\"parentId\":5}"
        }) {
      mvc.perform(
              api(put("/api/v1/topics/" + jvm + "/parent")).with(me()).with(csrf()).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(TopicService.MISSING_PARENT));
    }
  }

  @Test
  void aMoveCantCreateACycle() throws Exception {
    String a = create(me(), "A", null);
    String b = create(me(), "B", a);
    String c = create(me(), "C", b);

    for (String target : List.of(a, c)) {
      mvc.perform(
              api(put("/api/v1/topics/" + a + "/parent"))
                  .with(me())
                  .with(csrf())
                  .content("{\"parentId\":\"%s\"}".formatted(target)))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.detail").value(TopicService.CYCLE));
    }
  }

  @Test
  void aMoveKeepsSiblingNamesUnique() throws Exception {
    String a = create(me(), "A", null);
    create(me(), "Basics", a);
    String basics = create(me(), "basics", null);

    mvc.perform(
            api(put("/api/v1/topics/" + basics + "/parent"))
                .with(me())
                .with(csrf())
                .content("{\"parentId\":\"%s\"}".formatted(a)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(TopicService.DUPLICATE_NAME));
  }

  @Test
  void deletesALeafButNotATopicWithSubTopics() throws Exception {
    String java = create(me(), "Java", null);
    String jvm = create(me(), "JVM", java);

    mvc.perform(api(delete("/api/v1/topics/" + java)).with(me()).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(TopicService.HAS_CHILDREN));
    mvc.perform(api(delete("/api/v1/topics/" + jvm)).with(me()).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(api(delete("/api/v1/topics/" + java)).with(me()).with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  void anotherUsersTopicsAreNotFound() throws Exception {
    String theirs = create(other(), "Theirs", null);

    mvc.perform(api(get("/api/v1/topics")).with(me()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mvc.perform(
            api(put("/api/v1/topics/" + theirs + "/name"))
                .with(me())
                .with(csrf())
                .content("{\"name\":\"Mine now\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            api(put("/api/v1/topics/" + theirs + "/parent"))
                .with(me())
                .with(csrf())
                .content("{\"parentId\":null}"))
        .andExpect(status().isNotFound());
    mvc.perform(api(delete("/api/v1/topics/" + theirs)).with(me()).with(csrf()))
        .andExpect(status().isNotFound());
    // Their topic as my parent: the same 404, so ids can't be probed.
    mvc.perform(
            api(post("/api/v1/topics"))
                .with(me())
                .with(csrf())
                .content("{\"name\":\"Child\",\"parentId\":\"%s\"}".formatted(theirs)))
        .andExpect(status().isNotFound());
    String mine = create(me(), "Mine", null);
    mvc.perform(
            api(put("/api/v1/topics/" + mine + "/parent"))
                .with(me())
                .with(csrf())
                .content("{\"parentId\":\"%s\"}".formatted(theirs)))
        .andExpect(status().isNotFound());
  }

  @Test
  void writesNeedTheCsrfToken() throws Exception {
    mvc.perform(api(post("/api/v1/topics")).with(me()).content("{\"name\":\"Java\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void signedOutIs401() throws Exception {
    mvc.perform(api(get("/api/v1/topics"))).andExpect(status().isUnauthorized());
  }

  @Test
  void starterTopicsCreateTheSeedTreeOnce() throws Exception {
    mvc.perform(api(post("/api/v1/topics/starter")).with(me()).with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(37))
        // Tree order: the first root alphabetically, then its whole subtree.
        .andExpect(jsonPath("$[0].name").value("Build Tools"))
        .andExpect(jsonPath("$[1].name").value("Gradle"));

    assertThat(
            jdbc.queryForList(
                "select t.name from topic t join app_user u on u.id = t.owner_id"
                    + " where u.google_sub = 'sub-me' and t.parent_id is null order by t.name",
                String.class))
        .containsExactly(
            "Build Tools",
            "Cloud",
            "Databases",
            "Frameworks",
            "Fundamentals",
            "Languages",
            "Networking",
            "Web");

    assertThat(paths("sub-me"))
        .contains(
            "Languages › Java › JVM & Memory",
            "Frameworks › Spring › Spring Data JPA",
            "Web › Auth › OAuth2 / OIDC",
            "Cloud › GCP › Pub/Sub");

    mvc.perform(api(post("/api/v1/topics/starter")).with(me()).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(TopicService.NOT_EMPTY));
    assertThat(paths("sub-other")).isEmpty();
  }

  @Test
  void starterTopicsNeedAnEmptyList() throws Exception {
    create(me(), "Mine", null);

    mvc.perform(api(post("/api/v1/topics/starter")).with(me()).with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  void theDatabaseRejectsDuplicateTopLevelNamesIgnoringCase() {
    jdbc.update(
        "insert into topic (owner_id, name) select id, 'Java' from app_user where google_sub = 'sub-me'");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into topic (owner_id, name)"
                        + " select id, 'java' from app_user where google_sub = 'sub-me'"))
        .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void theDatabaseRejectsAParentOwnedByAnotherUser() {
    jdbc.update(
        "insert into topic (owner_id, name) select id, 'Theirs' from app_user where google_sub = 'sub-other'");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into topic (owner_id, parent_id, name)"
                        + " select me.id, t.id, 'Mine' from app_user me, topic t"
                        + " where me.google_sub = 'sub-me' and t.name = 'Theirs'"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("topic_parent_same_owner_fkey");
  }

  @Test
  void signedInWithoutAnAppUserRowIs401() throws Exception {
    mvc.perform(
            api(get("/api/v1/topics"))
                .with(oidcLogin().idToken(token -> token.subject("sub-nobody"))))
        .andExpect(status().isUnauthorized());
  }

  // Every topic's full path, built in SQL so the check doesn't reuse the code under test.
  private List<String> paths(String sub) {
    return jdbc.queryForList(
        """
        with recursive path (id, label) as (
          select t.id, t.name::text from topic t join app_user u on u.id = t.owner_id
           where u.google_sub = ? and t.parent_id is null
          union all
          select c.id, p.label || ' › ' || c.name from topic c join path p on c.parent_id = p.id
        )
        select label from path
        """,
        String.class,
        sub);
  }
}
