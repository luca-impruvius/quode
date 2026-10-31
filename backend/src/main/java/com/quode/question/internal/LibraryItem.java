package com.quode.question.internal;

import com.quode.taxonomy.TopicRef;
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
    Instant createdAt,
    Instant updatedAt) {}
