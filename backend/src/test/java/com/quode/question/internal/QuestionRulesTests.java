package com.quode.question.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.quode.question.internal.QuestionInput.OptionInput;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class QuestionRulesTests {

  private static final UUID T1 = UUID.randomUUID();
  private static final UUID T2 = UUID.randomUUID();

  private static QuestionInput flashcard() {
    return new QuestionInput(
        QuestionType.FLASHCARD,
        "What is a HashMap?",
        "A hash table.",
        null,
        2,
        null,
        List.of(T1),
        T1,
        List.of(),
        null);
  }

  private static QuestionInput choice(QuestionType type, List<OptionInput> options) {
    return new QuestionInput(type, "Pick one", null, null, 2, null, List.of(T1), T1, options, null);
  }

  private static List<OptionInput> options(boolean... correct) {
    List<OptionInput> options = new ArrayList<>();
    for (int i = 0; i < correct.length; i++) {
      options.add(new OptionInput("Option " + i, correct[i]));
    }
    return options;
  }

  private static String violation(QuestionInput input) {
    return QuestionRules.check(input).orElse(null);
  }

  @Test
  void aValidQuestionOfEachTypePasses() {
    assertThat(violation(flashcard())).isNull();
    assertThat(violation(choice(QuestionType.SINGLE_CHOICE, options(true, false)))).isNull();
    assertThat(violation(choice(QuestionType.MULTIPLE_CHOICE, options(true, true, false))))
        .isNull();
    assertThat(
            violation(
                new QuestionInput(
                    QuestionType.OPEN_TEXT,
                    "Explain GC",
                    "Model answer",
                    "Why",
                    3,
                    "https://docs.oracle.com",
                    List.of(T1, T2),
                    T2,
                    List.of(),
                    null)))
        .isNull();
  }

  @Test
  void needsAType() {
    assertThat(violation(with(flashcard(), null))).isEqualTo(QuestionRules.TYPE);
  }

  @Test
  void promptIsRequiredAndLimitedTo2000Characters() {
    assertThat(violation(prompt("   "))).isEqualTo(QuestionRules.PROMPT);
    assertThat(violation(prompt(null))).isEqualTo(QuestionRules.PROMPT);
    assertThat(violation(prompt("x".repeat(2000)))).isNull();
    assertThat(violation(prompt("x".repeat(2001)))).isEqualTo(QuestionRules.PROMPT);
  }

  @Test
  void answerIsRequiredForFlashcardAndOpenTextOnly() {
    QuestionInput f = flashcard();
    assertThat(violation(answer(f, " "))).isEqualTo(QuestionRules.ANSWER_REQUIRED);
    assertThat(violation(answer(f, "x".repeat(10_000)))).isNull();
    assertThat(violation(answer(f, "x".repeat(10_001)))).isEqualTo(QuestionRules.ANSWER_LENGTH);
    QuestionInput single = choice(QuestionType.SINGLE_CHOICE, options(true, false));
    assertThat(violation(single)).isNull();
  }

  @Test
  void explanationIsLimitedTo10000Characters() {
    QuestionInput f = flashcard();
    QuestionInput ok =
        new QuestionInput(
            f.type(),
            f.promptMd(),
            f.answerMd(),
            "x".repeat(10_000),
            2,
            null,
            f.topicIds(),
            f.primaryTopicId(),
            f.options(),
            null);
    QuestionInput tooLong =
        new QuestionInput(
            f.type(),
            f.promptMd(),
            f.answerMd(),
            "x".repeat(10_001),
            2,
            null,
            f.topicIds(),
            f.primaryTopicId(),
            f.options(),
            null);

    assertThat(violation(ok)).isNull();
    assertThat(violation(tooLong)).isEqualTo(QuestionRules.EXPLANATION);
  }

  @Test
  void difficultyIsOneToThree() {
    for (Integer bad : new Integer[] {null, 0, 4}) {
      assertThat(violation(difficulty(bad))).isEqualTo(QuestionRules.DIFFICULTY);
    }
    assertThat(violation(difficulty(1))).isNull();
    assertThat(violation(difficulty(3))).isNull();
  }

  @Test
  void sourceUrlIsAnHttpLinkUpTo2000Characters() {
    assertThat(violation(source("ftp://example.com"))).isEqualTo(QuestionRules.SOURCE);
    assertThat(violation(source("javascript:alert(1)"))).isEqualTo(QuestionRules.SOURCE);
    assertThat(violation(source("https://example.com/" + "x".repeat(1981))))
        .isEqualTo(QuestionRules.SOURCE);
    assertThat(violation(source("http://example.com"))).isNull();
    assertThat(violation(source(""))).isNull();
  }

  @Test
  void needsOneToFiveDistinctTopicsWithThePrimaryAmongThem() {
    assertThat(violation(topics(List.of(), T1))).isEqualTo(QuestionRules.TOPICS);
    List<UUID> five = List.of(T1, T2, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    assertThat(violation(topics(five, T1))).isNull();
    List<UUID> six = new ArrayList<>(five);
    six.add(UUID.randomUUID());
    assertThat(violation(topics(six, T1))).isEqualTo(QuestionRules.TOPICS);
    assertThat(violation(topics(List.of(T1, T1), T1))).isEqualTo(QuestionRules.TOPICS);
    assertThat(violation(topics(List.of(T1), T2))).isEqualTo(QuestionRules.PRIMARY);
    assertThat(violation(topics(List.of(T1), null))).isEqualTo(QuestionRules.PRIMARY);
  }

  @ParameterizedTest
  @EnumSource(
      value = QuestionType.class,
      names = {"SINGLE_CHOICE", "MULTIPLE_CHOICE"})
  void choiceQuestionsHaveTwoToSixOptions(QuestionType type) {
    assertThat(violation(choice(type, options(true)))).isEqualTo(QuestionRules.OPTION_COUNT);
    assertThat(violation(choice(type, options(true, false, false, false, false, false)))).isNull();
    assertThat(violation(choice(type, options(true, false, false, false, false, false, false))))
        .isEqualTo(QuestionRules.OPTION_COUNT);
  }

  @Test
  void singleChoiceHasExactlyOneCorrectOption() {
    assertThat(violation(choice(QuestionType.SINGLE_CHOICE, options(false, false))))
        .isEqualTo(QuestionRules.SINGLE_CORRECT);
    assertThat(violation(choice(QuestionType.SINGLE_CHOICE, options(true, true))))
        .isEqualTo(QuestionRules.SINGLE_CORRECT);
  }

  @Test
  void multipleChoiceHasAtLeastOneCorrectOption() {
    assertThat(violation(choice(QuestionType.MULTIPLE_CHOICE, options(false, false))))
        .isEqualTo(QuestionRules.MULTIPLE_CORRECT);
    assertThat(violation(choice(QuestionType.MULTIPLE_CHOICE, options(true, true)))).isNull();
  }

  @Test
  void optionTextIsRequiredAndLimited() {
    List<OptionInput> blank = List.of(new OptionInput(" ", true), new OptionInput("B", false));
    List<OptionInput> tooLong =
        List.of(new OptionInput("x".repeat(2001), true), new OptionInput("B", false));

    assertThat(violation(choice(QuestionType.SINGLE_CHOICE, blank)))
        .isEqualTo(QuestionRules.OPTION_TEXT);
    assertThat(violation(choice(QuestionType.SINGLE_CHOICE, tooLong)))
        .isEqualTo(QuestionRules.OPTION_TEXT);
  }

  @ParameterizedTest
  @EnumSource(
      value = QuestionType.class,
      names = {"FLASHCARD", "OPEN_TEXT"})
  void flashcardsAndOpenTextHaveNoOptions(QuestionType type) {
    QuestionInput f = flashcard();
    QuestionInput withOptions =
        new QuestionInput(
            type,
            f.promptMd(),
            f.answerMd(),
            null,
            2,
            null,
            f.topicIds(),
            f.primaryTopicId(),
            options(true, false),
            null);

    assertThat(violation(withOptions)).isEqualTo(QuestionRules.NO_OPTIONS);
  }

  private static QuestionInput with(QuestionInput f, QuestionType type) {
    return new QuestionInput(
        type,
        f.promptMd(),
        f.answerMd(),
        f.explanationMd(),
        f.difficulty(),
        f.sourceUrl(),
        f.topicIds(),
        f.primaryTopicId(),
        f.options(),
        null);
  }

  private static QuestionInput prompt(String prompt) {
    QuestionInput f = flashcard();
    return new QuestionInput(
        f.type(),
        prompt,
        f.answerMd(),
        null,
        2,
        null,
        f.topicIds(),
        f.primaryTopicId(),
        f.options(),
        null);
  }

  private static QuestionInput answer(QuestionInput f, String answer) {
    return new QuestionInput(
        f.type(),
        f.promptMd(),
        answer,
        null,
        2,
        null,
        f.topicIds(),
        f.primaryTopicId(),
        f.options(),
        null);
  }

  private static QuestionInput difficulty(Integer difficulty) {
    QuestionInput f = flashcard();
    return new QuestionInput(
        f.type(),
        f.promptMd(),
        f.answerMd(),
        null,
        difficulty,
        null,
        f.topicIds(),
        f.primaryTopicId(),
        f.options(),
        null);
  }

  private static QuestionInput source(String url) {
    QuestionInput f = flashcard();
    return new QuestionInput(
        f.type(),
        f.promptMd(),
        f.answerMd(),
        null,
        2,
        url,
        f.topicIds(),
        f.primaryTopicId(),
        f.options(),
        null);
  }

  private static QuestionInput topics(List<UUID> ids, UUID primary) {
    QuestionInput f = flashcard();
    return new QuestionInput(
        f.type(), f.promptMd(), f.answerMd(), null, 2, null, ids, primary, f.options(), null);
  }
}
