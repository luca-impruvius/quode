package com.quode.question;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read access to questions for studying (quizzes, M2). Every method is per owner. */
public interface QuestionCatalog {

  /**
   * The owner's studyable questions (ACTIVE, not archived) with a topic in any of the topics'
   * trees, oldest first. Unknown and other users' topics match nothing.
   */
  List<UUID> studyableIds(UUID ownerId, Collection<UUID> topicIds);

  /** Of {@code ids}, the ones that are the owner's and studyable, in the given order. */
  List<UUID> keepStudyable(UUID ownerId, List<UUID> ids);

  /** The owner's questions among {@code ids}, archived and draft ones included, by id. */
  Map<UUID, StudyQuestion> find(UUID ownerId, Collection<UUID> ids);

  /** A question as studying needs it, answers included: callers decide what to show. */
  record StudyQuestion(
      UUID id,
      Type type,
      String promptMd,
      String answerMd,
      String explanationMd,
      List<Option> options) {

    public record Option(UUID id, String textMd, boolean correct) {}
  }

  /** The question types, as studying grades them. */
  enum Type {
    FLASHCARD,
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    OPEN_TEXT;

    public boolean isChoice() {
      return this == SINGLE_CHOICE || this == MULTIPLE_CHOICE;
    }
  }
}
