package com.quode.question.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A question as the API returns it. Topics: the primary first, then by path. */
record QuestionView(
    UUID id,
    QuestionType type,
    String promptMd,
    String answerMd,
    String explanationMd,
    int difficulty,
    QuestionStatus status,
    String sourceUrl,
    List<TopicItem> topics,
    List<OptionItem> options,
    Instant flaggedAt,
    String flagNote,
    Instant archivedAt,
    long version,
    Instant createdAt,
    Instant updatedAt) {

  record TopicItem(UUID id, String name, String path, boolean primary) {}

  record OptionItem(UUID id, String textMd, boolean correct) {}
}
