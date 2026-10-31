package com.quode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The API contract (M1-08): the committed spec in frontend/src/api/openapi.json must match what the
 * backend serves, and must be exact enough to generate types from. {@code ./gradlew updateOpenApi}
 * rewrites the file instead of comparing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class OpenApiContractTests {

  static final Path SPEC = Path.of("..", "frontend", "src", "api", "openapi.json");

  private static final JsonMapper JSON =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  private static String served;

  @BeforeAll
  static void reset() {
    served = null;
  }

  private String served() throws Exception {
    if (served == null) {
      String raw =
          mvc.perform(get("/api/v3/api-docs").contextPath("/api"))
              .andReturn()
              .getResponse()
              .getContentAsString(StandardCharsets.UTF_8);
      served = JSON.writeValueAsString(JSON.readTree(raw)).replace("\r\n", "\n") + "\n";
    }
    return served;
  }

  private JsonNode spec() throws Exception {
    return JSON.readTree(served());
  }

  @Test
  void theCommittedSpecMatchesTheServedOne() throws Exception {
    if (Boolean.getBoolean("openapi.update")) {
      Files.writeString(SPEC, served(), StandardCharsets.UTF_8);
      return;
    }
    String committed =
        Files.exists(SPEC)
            ? Files.readString(SPEC, StandardCharsets.UTF_8).replace("\r\n", "\n")
            : "";
    assertThat(committed)
        .as(
            "The API changed: run ./gradlew updateOpenApi in backend/, then npm run generate:api in"
                + " frontend/, and commit both files.")
        .isEqualTo(served());
  }

  @Test
  void everySuccessResponsePropertyIsRequired() throws Exception {
    JsonNode schemas = spec().path("components").path("schemas");
    for (String name :
        List.of("QuestionView", "TopicView", "LibraryPage", "LibraryItem", "TopicRef")) {
      JsonNode schema = schemas.path(name);
      Set<String> properties = new HashSet<>(schema.path("properties").propertyNames());
      Set<String> required = new HashSet<>();
      schema.path("required").forEach(n -> required.add(n.asString()));
      assertThat(required).as(name).isEqualTo(properties);
    }
  }

  @Test
  void requestBodiesKeepTheirOptionalFields() throws Exception {
    assertThat(spec().path("components").path("schemas").path("QuestionInput").has("required"))
        .isFalse();
  }

  @Test
  void responsesAreJson() throws Exception {
    JsonNode content =
        spec()
            .path("paths")
            .path("/v1/questions/{id}")
            .path("get")
            .path("responses")
            .path("200")
            .path("content");
    assertThat(content.propertyNames()).containsExactly("application/json");
  }

  /**
   * The fields marked nullable are exactly the ones real responses send as null: a flashcard with
   * no explanation, source, flag or archive, a top-level topic, and an unflagged Library row.
   */
  @Test
  void nullableFieldsAreExactlyTheOnesThatCanBeNull() throws Exception {
    UUID owner =
        jdbc.queryForObject(
            "insert into app_user (email, google_sub, display_name)"
                + " values ('me@example.com', 'sub-me', 'Me') returning id",
            UUID.class);
    UUID topic =
        jdbc.queryForObject(
            "insert into topic (owner_id, name) values (?, 'Java') returning id",
            UUID.class,
            owner);
    var me = oidcLogin().idToken(token -> token.subject("sub-me"));
    String created =
        mvc.perform(
                post("/api/v1/questions")
                    .contextPath("/api")
                    .with(me)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"type":"SINGLE_CHOICE","promptMd":"Pick","difficulty":2,"topicIds":["%s"],
                         "primaryTopicId":"%s","options":[{"textMd":"A","correct":true},
                         {"textMd":"B","correct":false}]}
                        """
                            .formatted(topic, topic)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String topics =
        mvc.perform(get("/api/v1/topics").contextPath("/api").with(me))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String library =
        mvc.perform(get("/api/v1/questions").contextPath("/api").with(me))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode schemas = spec().path("components").path("schemas");
    assertThat(nullFields(JSON.readTree(created))).isEqualTo(nullableIn(schemas, "QuestionView"));
    assertThat(nullFields(JSON.readTree(topics).get(0)))
        .isEqualTo(nullableIn(schemas, "TopicView"));
    assertThat(nullFields(JSON.readTree(library).path("items").get(0)))
        .isEqualTo(nullableIn(schemas, "LibraryItem"));
  }

  private static Set<String> nullFields(JsonNode object) {
    Set<String> nulls = new HashSet<>();
    for (Map.Entry<String, JsonNode> field : object.properties()) {
      if (field.getValue().isNull()) {
        nulls.add(field.getKey());
      }
    }
    return nulls;
  }

  private static Set<String> nullableIn(JsonNode schemas, String name) {
    Set<String> nullable = new HashSet<>();
    for (Map.Entry<String, JsonNode> property :
        schemas.path(name).path("properties").properties()) {
      List<String> types = new ArrayList<>();
      property.getValue().path("type").forEach(t -> types.add(t.asString()));
      if (types.contains("null")) {
        nullable.add(property.getKey());
      }
    }
    return nullable;
  }
}
