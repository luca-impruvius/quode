package com.quode.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

class OpenApiConfigTests {

  private final OpenApiConfig config = new OpenApiConfig();

  @Test
  void marksResponsePropertiesRequiredAndLeavesRequestsAlone() {
    OpenAPI api = api("Input", "View");

    config.responsePropertiesAreRequired().customise(api);

    assertThat(api.getComponents().getSchemas().get("View").getRequired())
        .containsExactly("a", "b");
    assertThat(api.getComponents().getSchemas().get("Input").getRequired()).isNull();
  }

  @Test
  void refusesASchemaUsedAsBothRequestAndResponse() {
    OpenAPI api = api("View", "View");

    assertThatThrownBy(() -> config.responsePropertiesAreRequired().customise(api))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("[View]");
  }

  private static OpenAPI api(String requestSchema, String responseSchema) {
    Operation post =
        new Operation()
            .requestBody(new RequestBody().content(json(requestSchema)))
            .responses(
                new ApiResponses()
                    .addApiResponse("200", new ApiResponse().content(json(responseSchema))));
    return new OpenAPI()
        .paths(new Paths().addPathItem("/things", new PathItem().post(post)))
        .components(
            new Components().addSchemas("Input", object()).addSchemas("View", object()));
  }

  private static Content json(String schema) {
    return new Content()
        .addMediaType(
            "application/json",
            new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + schema)));
  }

  private static Schema<?> object() {
    return new ObjectSchema().addProperty("b", new StringSchema()).addProperty("a", new StringSchema());
  }
}
