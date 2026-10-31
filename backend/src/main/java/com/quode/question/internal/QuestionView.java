package com.quode.question.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A question as the API returns it. Topics: the primary first, then by path. */
record QuestionView(
    UUID id,
    QuestionType type,
    String promptMd,
    @Schema(types = {"string", "null"}) String answerMd,
    @Schema(types = {"string", "null"}) String explanationMd,
    int difficulty,
    QuestionStatus status,
    @Schema(types = {"string", "null"}) String sourceUrl,
    List<TopicItem> topics,
    List<OptionItem> options,
    @Schema(types = {"string", "null"}) Instant flaggedAt,
    @Schema(types = {"string", "null"}) String flagNote,
    @Schema(types = {"string", "null"}) Instant archivedAt,
    long version,
    Instant createdAt,
    Instant updatedAt) {

  record TopicItem(UUID id, String name, String path, boolean primary) {}

  record OptionItem(UUID id, String textMd, boolean correct) {}
}
