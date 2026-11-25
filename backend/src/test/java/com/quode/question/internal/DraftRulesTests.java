package com.quode.question.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.quode.question.internal.QuestionInput.OptionInput;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The DRAFT rules and duplicate key of bulk import (M2-01). */
class DraftRulesTests {

  private static final UUID TOPIC = UUID.randomUUID();

  private static QuestionInput question(QuestionType type, String answer, OptionInput... options) {
    return new QuestionInput(
        type, "Prompt?", answer, null, 2, null, List.of(TOPIC), TOPIC, List.of(options), null);
  }

  @Test
  void aChoiceQuestionWithoutACorrectOptionNeedsAnAnswerButIsAValidDraft() {
    QuestionInput q =
        question(
            QuestionType.SINGLE_CHOICE,
            null,
            new OptionInput("A", false),
            new OptionInput("B", false));
    assertThat(QuestionRules.needsAnswer(q)).isTrue();
    assertThat(QuestionRules.checkDraft(q)).isEmpty();
    assertThat(QuestionRules.check(q)).contains(QuestionRules.SINGLE_CORRECT);
  }

  @Test
  void aChoiceDraftMayHaveNoOptionsYetButNotJustOne() {
    assertThat(QuestionRules.checkDraft(question(QuestionType.MULTIPLE_CHOICE, null))).isEmpty();
    assertThat(
            QuestionRules.checkDraft(
                question(QuestionType.MULTIPLE_CHOICE, null, new OptionInput("A", false))))
        .contains(QuestionRules.OPTION_COUNT);
  }

  @Test
  void aDraftStillFollowsTheOtherRules() {
    QuestionInput twoCorrect =
        question(
            QuestionType.SINGLE_CHOICE,
            null,
            new OptionInput("A", true),
            new OptionInput("B", true));
    assertThat(QuestionRules.needsAnswer(twoCorrect)).isFalse();
    assertThat(QuestionRules.check(twoCorrect)).contains(QuestionRules.SINGLE_CORRECT);
    assertThat(
            QuestionRules.checkDraft(
                question(QuestionType.FLASHCARD, null, new OptionInput("A", true))))
        .contains(QuestionRules.NO_OPTIONS);
  }

  @Test
  void aFlashcardOrOpenQuestionWithoutAnswerTextIsADraft() {
    QuestionInput blank = question(QuestionType.OPEN_TEXT, "  ");
    assertThat(QuestionRules.needsAnswer(blank)).isTrue();
    assertThat(QuestionRules.checkDraft(blank)).isEmpty();
    assertThat(QuestionRules.check(blank)).contains(QuestionRules.ANSWER_REQUIRED);
    assertThat(QuestionRules.needsAnswer(question(QuestionType.FLASHCARD, "An answer"))).isFalse();
  }

  @Test
  void duplicatesCompareIgnoringCaseAndRepeatedSpaces() {
    assertThat(ImportService.normalize("  What  is\tPub/Sub? "))
        .isEqualTo(ImportService.normalize("what is pub/sub?"));
    assertThat(ImportService.normalize("What is GKE?"))
        .isNotEqualTo(ImportService.normalize("What is GCE?"));
    assertThat(ImportService.normalize("What is GKE?")).isEqualTo("what is gke?");
  }
}
