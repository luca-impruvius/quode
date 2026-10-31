package com.quode.shared.internal;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the OpenAPI spec exact enough to generate the frontend's types from it (M1-08): every
 * property of a success-response schema is required, because our response records always serialize
 * every field (nulls included). Fields that can be null say so on the record component with
 * {@code @Schema(types = {"string", "null"})}. Request schemas keep their real optional fields.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

  @Bean
  OpenApiCustomizer responsePropertiesAreRequired() {
    return openApi -> {
      if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
        return;
      }
      @SuppressWarnings("rawtypes")
      Map<String, Schema> schemas = openApi.getComponents().getSchemas();
      Set<String> responseSchemas = new HashSet<>();
      openApi
          .getPaths()
          .values()
          .forEach(
              item ->
                  item.readOperations().stream()
                      .map(Operation::getResponses)
                      .filter(responses -> responses != null)
                      .forEach(
                          responses ->
                              responses.forEach(
                                  (code, response) -> {
                                    if (code.startsWith("2")) {
                                      collect(response, schemas, responseSchemas);
                                    }
                                  })));
      for (String name : responseSchemas) {
        Schema<?> schema = schemas.get(name);
        if (schema != null && schema.getProperties() != null) {
          schema.setRequired(new ArrayList<>(new TreeSet<>(schema.getProperties().keySet())));
        }
      }
    };
  }

  @SuppressWarnings("rawtypes")
  private static void collect(ApiResponse response, Map<String, Schema> schemas, Set<String> out) {
    Content content = response.getContent();
    if (content != null) {
      for (MediaType mediaType : content.values()) {
        collect(mediaType.getSchema(), schemas, out);
      }
    }
  }

  // Every component schema reachable from a response: the response type and everything it nests.
  @SuppressWarnings("rawtypes")
  private static void collect(Schema<?> schema, Map<String, Schema> schemas, Set<String> out) {
    if (schema == null) {
      return;
    }
    if (schema.get$ref() != null) {
      String name = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
      if (out.add(name)) {
        collect(schemas.get(name), schemas, out);
      }
      return;
    }
    collect(schema.getItems(), schemas, out);
    if (schema.getProperties() != null) {
      schema.getProperties().values().forEach(p -> collect(p, schemas, out));
    }
    for (List<Schema> group :
        new ArrayList<List<Schema>>(
            java.util.Arrays.asList(schema.getAllOf(), schema.getAnyOf(), schema.getOneOf()))) {
      if (group != null) {
        group.forEach(s -> collect(s, schemas, out));
      }
    }
  }
}
