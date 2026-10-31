package com.quode.question.internal;

/** docs/domain.md §2. Choice types carry answer options; the others carry a written answer. */
enum QuestionType {
  FLASHCARD,
  SINGLE_CHOICE,
  MULTIPLE_CHOICE,
  OPEN_TEXT;

  boolean isChoice() {
    return this == SINGLE_CHOICE || this == MULTIPLE_CHOICE;
  }
}
