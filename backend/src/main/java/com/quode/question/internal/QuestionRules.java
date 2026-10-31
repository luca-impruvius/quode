package com.quode.question.internal;

import com.quode.question.internal.QuestionInput.OptionInput;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The question rules (docs/specs/M1-questions.md, domain.md §4) as a pure function: the first
 * broken rule's user-facing message, or empty. Topic ownership is checked by the service.
 */
final class QuestionRules {

  static final String TYPE = "Choose a question type.";
  static final String PROMPT = "The question needs 1 to 2,000 characters.";
  static final String ANSWER_REQUIRED = "This question type needs an answer.";
  static final String ANSWER_LENGTH = "The answer can have up to 10,000 characters.";
  static final String EXPLANATION = "The explanation can have up to 10,000 characters.";
  static final String DIFFICULTY = "Difficulty is 1, 2 or 3.";
  static final String SOURCE =
      "The source link must start with http:// or https:// (up to 2,000 characters).";
  static final String TOPICS = "Choose 1 to 5 different topics.";
  static final String PRIMARY = "Choose one of the question's topics as the primary topic.";
  static final String OPTION_COUNT = "A choice question needs 2 to 6 options.";
  static final String OPTION_TEXT = "Every option needs 1 to 2,000 characters.";
  static final String SINGLE_CORRECT = "Mark exactly one option as correct.";
  static final String MULTIPLE_CORRECT = "Mark at least one option as correct.";
  static final String NO_OPTIONS = "Only choice questions have options.";

  static final int MAX_PROMPT = 2_000;
  static final int MAX_TEXT = 10_000;
  static final int MAX_OPTION = 2_000;
  static final int MAX_SOURCE = 2_000;
  static final int MAX_TOPICS = 5;
  static final int MIN_OPTIONS = 2;
  static final int MAX_OPTIONS = 6;

  private static final Pattern HTTP_URL = Pattern.compile("^https?://\\S+$");

  private QuestionRules() {}

  static Optional<String> check(QuestionInput q) {
    if (q.type() == null) {
      return Optional.of(TYPE);
    }
    if (isBlank(q.promptMd()) || tooLong(q.promptMd(), MAX_PROMPT)) {
      return Optional.of(PROMPT);
    }
    if (!q.type().isChoice() && isBlank(q.answerMd())) {
      return Optional.of(ANSWER_REQUIRED);
    }
    if (tooLong(q.answerMd(), MAX_TEXT)) {
      return Optional.of(ANSWER_LENGTH);
    }
    if (tooLong(q.explanationMd(), MAX_TEXT)) {
      return Optional.of(EXPLANATION);
    }
    if (q.difficulty() == null || q.difficulty() < 1 || q.difficulty() > 3) {
      return Optional.of(DIFFICULTY);
    }
    if (!isBlank(q.sourceUrl())
        && (tooLong(q.sourceUrl(), MAX_SOURCE) || !HTTP_URL.matcher(q.sourceUrl()).matches())) {
      return Optional.of(SOURCE);
    }
    List<UUID> topics = q.topicIdsOrEmpty();
    if (topics.isEmpty()
        || topics.size() > MAX_TOPICS
        || topics.stream().anyMatch(Objects::isNull)
        || new HashSet<>(topics).size() != topics.size()) {
      return Optional.of(TOPICS);
    }
    if (q.primaryTopicId() == null || !topics.contains(q.primaryTopicId())) {
      return Optional.of(PRIMARY);
    }
    return checkOptions(q.type(), q.optionsOrEmpty());
  }

  private static Optional<String> checkOptions(QuestionType type, List<OptionInput> options) {
    if (!type.isChoice()) {
      return options.isEmpty() ? Optional.empty() : Optional.of(NO_OPTIONS);
    }
    if (options.size() < MIN_OPTIONS
        || options.size() > MAX_OPTIONS
        || options.stream().anyMatch(Objects::isNull)) {
      return Optional.of(OPTION_COUNT);
    }
    for (OptionInput option : options) {
      if (isBlank(option.textMd()) || tooLong(option.textMd(), MAX_OPTION)) {
        return Optional.of(OPTION_TEXT);
      }
    }
    long correct = options.stream().filter(OptionInput::correct).count();
    if (type == QuestionType.SINGLE_CHOICE && correct != 1) {
      return Optional.of(SINGLE_CORRECT);
    }
    if (type == QuestionType.MULTIPLE_CHOICE && correct < 1) {
      return Optional.of(MULTIPLE_CORRECT);
    }
    return Optional.empty();
  }

  // Characters as PostgreSQL's varchar counts them (code points), so an emoji counts once.
  private static boolean tooLong(String text, int max) {
    return text != null && text.codePointCount(0, text.length()) > max;
  }

  static boolean isBlank(String text) {
    return text == null || text.isBlank();
  }
}
