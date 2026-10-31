package com.quode.question.internal;

import com.quode.taxonomy.TopicRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One row of the Library list. */
record LibraryItem(
    UUID id,
    QuestionType type,
    String promptMd,
    int difficulty,
    TopicRef primaryTopic,
    boolean archived,
    boolean flagged,
    @Schema(types = {"string", "null"}) String flagNote,
    Instant createdAt,
    Instant updatedAt) {}
