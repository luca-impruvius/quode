package com.quode.question.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.quode.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** Questions seen from the topic tree: the real question module behind taxonomy's TopicUsage. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class QuestionTopicUsageTests {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  private UUID java;
  private UUID collections;
  private UUID web;

  @BeforeEach
  void data() {
    UUID me =
        jdbc.queryForObject(
            "insert into app_user (email, google_sub, display_name)"
                + " values ('me@example.com', 'sub-me', 'Me') returning id",
            UUID.class);
    java = topic(me, null, "Java");
    collections = topic(me, java, "Collections");
    web = topic(me, null, "Web");
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

  private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder request) {
    return request.contextPath("/api").contentType(MediaType.APPLICATION_JSON);
  }

  private String question(UUID primary, UUID... others) throws Exception {
    StringBuilder ids = new StringBuilder("\"" + primary + "\"");
    for (UUID t : others) {
      ids.append(",\"").append(t).append('"');
    }
    String json =
        mvc.perform(
                api(post("/api/v1/questions"))
                    .with(me())
                    .with(csrf())
                    .content(
                        """
                        {"type":"FLASHCARD","promptMd":"Q","answerMd":"A","difficulty":2,
                         "topicIds":[%s],"primaryTopicId":"%s"}
                        """
                            .formatted(ids, primary)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(json, "$.id");
  }

  private Map<String, Integer> counts() throws Exception {
    String json =
        mvc.perform(api(get("/api/v1/topics")).with(me()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> names = JsonPath.read(json, "$[*].name");
    List<Integer> counts = JsonPath.read(json, "$[*].questionCount");
    Map<String, Integer> byName = new java.util.HashMap<>();
    for (int i = 0; i < names.size(); i++) {
      byName.put(names.get(i), counts.get(i));
    }
    return byName;
  }

  @Test
  void countsIncludeSubTopicsCountEachQuestionOnceAndSkipArchived() throws Exception {
    question(collections, java);
    question(collections);
    String archived = question(java);
    mvc.perform(
            api(post("/api/v1/questions/" + archived + "/archive"))
                .with(me())
                .with(csrf())
                .content("{\"version\":0}"))
        .andExpect(status().isOk());

    assertThat(counts())
        .containsEntry("Java", 2)
        .containsEntry("Collections", 2)
        .containsEntry("Web", 0);
  }

  @Test
  void aTopicUsedOnlyByAnArchivedQuestionCantBeDeleted() throws Exception {
    String id = question(web);
    mvc.perform(
            api(post("/api/v1/questions/" + id + "/archive"))
                .with(me())
                .with(csrf())
                .content("{\"version\":0}"))
        .andExpect(status().isOk());

    mvc.perform(api(delete("/api/v1/topics/" + web)).with(me()).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Questions use this topic."));
  }
}
