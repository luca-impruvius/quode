package com.quode.question.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Questions exported in the import format, with ids (docs/specs/M2-exam-prep-mvp.md, export and
 * fill): answer them outside the app and import the file back. {@code topic} is always null; each
 * question lists its own topic paths, the primary one first.
 */
record ExportFile(@Schema(types = {"string", "null"}) String topic, List<ExportItem> questions) {

  record ExportItem(
      UUID id,
      QuestionType type,
      String prompt,
      @Schema(types = {"string", "null"}) String answer,
      List<ExportOption> options,
      @Schema(types = {"string", "null"}) String explanation,
      int difficulty,
      @Schema(types = {"string", "null"}) String sourceUrl,
      List<String> topics) {}

  record ExportOption(String text, boolean correct) {}
}
