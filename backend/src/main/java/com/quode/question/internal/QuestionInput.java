package com.quode.question.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * A question as the client sends it, for create and edit (a full replace). {@code options} are in
 * display order. {@code version} is the one the client read: required on edit, ignored on create.
 */
record QuestionInput(
    QuestionType type,
    String promptMd,
    @Schema(types = {"string", "null"}) String answerMd,
    @Schema(types = {"string", "null"}) String explanationMd,
    Integer difficulty,
    @Schema(types = {"string", "null"}) String sourceUrl,
    List<UUID> topicIds,
    UUID primaryTopicId,
    List<OptionInput> options,
    Long version) {

  record OptionInput(String textMd, boolean correct) {}

  List<UUID> topicIdsOrEmpty() {
    return topicIds == null ? List.of() : topicIds;
  }

  List<OptionInput> optionsOrEmpty() {
    return options == null ? List.of() : options;
  }
}
