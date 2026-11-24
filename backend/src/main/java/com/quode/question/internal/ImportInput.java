package com.quode.question.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A bulk import (docs/specs/M2-exam-prep-mvp.md). {@code topic}: the path ("Cloud › GCP") every
 * question goes under, as its primary topic. Fields are loose on purpose (type as text, everything
 * optional), so one bad question becomes a line in the report instead of rejecting the file.
 */
record ImportInput(@Schema(types = {"string", "null"}) String topic, List<ImportItem> questions) {

  /**
   * One question. With {@code id}: fills in that DRAFT question's answer (export and fill).
   * Without: a new question, saved as DRAFT when it has no correct option or no answer. {@code
   * type}: FLASHCARD, SINGLE_CHOICE, MULTIPLE_CHOICE or OPEN_TEXT. {@code difficulty}: 1–3, default
   * 2. {@code topics}: extra topic paths.
   */
  record ImportItem(
      @Schema(types = {"string", "null"}) String id,
      @Schema(types = {"string", "null"}) String type,
      @Schema(types = {"string", "null"}) String prompt,
      @Schema(types = {"string", "null"}) String answer,
      List<ImportOption> options,
      @Schema(types = {"string", "null"}) String explanation,
      Integer difficulty,
      @Schema(types = {"string", "null"}) String sourceUrl,
      List<String> topics) {}

  record ImportOption(String text, Boolean correct) {}
}
