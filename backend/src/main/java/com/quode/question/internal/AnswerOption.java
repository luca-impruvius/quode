package com.quode.question.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** One choice of a choice question; {@code position} is its display order (0-based). */
@Entity
@Table(name = "answer_option")
class AnswerOption {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false, updatable = false)
  private Question question;

  @Column(name = "text_md", nullable = false, length = QuestionRules.MAX_OPTION)
  private String textMd;

  @Column(name = "is_correct", nullable = false)
  private boolean correct;

  @Column(nullable = false)
  private short position;

  protected AnswerOption() {
    // For JPA.
  }

  AnswerOption(Question question, String textMd, boolean correct, short position) {
    this.question = question;
    this.textMd = textMd;
    this.correct = correct;
    this.position = position;
  }

  UUID getId() {
    return id;
  }

  String getTextMd() {
    return textMd;
  }

  boolean isCorrect() {
    return correct;
  }
}
