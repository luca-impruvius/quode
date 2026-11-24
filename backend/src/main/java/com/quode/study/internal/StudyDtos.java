package com.quode.study.internal;

import com.quode.question.QuestionCatalog;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The study API's requests and responses (docs/specs/M2-exam-prep-mvp.md, Quick quiz). */
final class StudyDtos {

  private StudyDtos() {}

  /**
   * Start a quick quiz: from topics (with their sub-topics), or from given questions (Retry
   * missed). {@code count} null means all; {@code mode} ALL, UNSEEN or MISSED (default ALL).
   */
  record StartSession(
      @Schema(types = {"string", "null"}) String kind,
      List<UUID> topicIds,
      List<UUID> questionIds,
      Integer count,
      @Schema(types = {"string", "null"}) String mode,
      Boolean shuffle) {}

  /** A question to answer, without its answer. */
  record QuestionCard(
      UUID id, QuestionCatalog.Type type, String promptMd, List<CardOption> options) {}

  record CardOption(UUID id, String textMd) {}

  /**
   * A session's progress. {@code current} is the next question to answer, null once every question
   * is answered.
   */
  record SessionView(
      UUID id,
      String kind,
      int total,
      int answered,
      int correct,
      @Schema(types = {"object", "null"}) QuestionCard current,
      @Schema(types = {"string", "null"}) Instant finishedAt) {}

  /**
   * An answer: {@code selectedOptionIds} for choice questions, {@code grade} (GOT_IT, PARTIAL,
   * MISSED) for flashcard and open questions.
   */
  record AnswerInput(
      UUID questionId,
      List<UUID> selectedOptionIds,
      @Schema(types = {"string", "null"}) String grade) {}

  /** How the answer went, with what to show after it. */
  record AnswerResult(
      String outcome,
      List<UUID> correctOptionIds,
      @Schema(types = {"string", "null"}) String answerMd,
      @Schema(types = {"string", "null"}) String explanationMd,
      SessionView session) {}

  /** The score, and the questions to retry (wrong or partial). */
  record FinishResult(
      int total, int answered, int correct, int partial, int wrong, List<UUID> missedQuestionIds) {}
}
