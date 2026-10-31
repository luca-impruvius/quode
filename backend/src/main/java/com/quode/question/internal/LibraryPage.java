package com.quode.question.internal;

import com.quode.taxonomy.TopicRef;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One page of the Library list, with the total for "N questions" (ADR-0030). */
record LibraryPage(List<Item> items, int page, int size, long totalElements, boolean hasNext) {

  record Item(
      UUID id,
      QuestionType type,
      String promptMd,
      int difficulty,
      TopicRef primaryTopic,
      boolean archived,
      boolean flagged,
      Instant createdAt,
      Instant updatedAt) {}
}
