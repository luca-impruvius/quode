package com.quode.question.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.CurrentTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

/**
 * A question and its children (options, topic links), saved as one aggregate. {@code version} is
 * the optimistic lock: changing any field or child bumps it.
 */
@Entity
@Table(name = "question")
class Question {

  /** One row of question_topic; the owner column lets the database keep topics same-owner. */
  @Embeddable
  static class TopicLink {

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    protected TopicLink() {
      // For JPA.
    }

    TopicLink(UUID topicId, UUID ownerId) {
      this.topicId = topicId;
      this.ownerId = ownerId;
    }

    UUID getTopicId() {
      return topicId;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof TopicLink link
          && topicId.equals(link.topicId)
          && ownerId.equals(link.ownerId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(topicId, ownerId);
    }
  }

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private UUID ownerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private QuestionType type;

  @Column(name = "prompt_md", nullable = false, length = QuestionRules.MAX_PROMPT)
  private String promptMd;

  @Column(name = "answer_md", length = QuestionRules.MAX_TEXT)
  private String answerMd;

  @Column(name = "explanation_md", length = QuestionRules.MAX_TEXT)
  private String explanationMd;

  @Column(nullable = false)
  private short difficulty;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private QuestionStatus status = QuestionStatus.ACTIVE;

  @Column(name = "source_url", length = QuestionRules.MAX_SOURCE)
  private String sourceUrl;

  @Column(name = "primary_topic_id", nullable = false)
  private UUID primaryTopicId;

  @Column(name = "flagged_at")
  private Instant flaggedAt;

  @Column(name = "flag_note", length = 200)
  private String flagNote;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Version private long version;

  @CurrentTimestamp(event = EventType.INSERT, source = SourceType.DB)
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @CurrentTimestamp(source = SourceType.DB)
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position")
  private List<AnswerOption> options = new ArrayList<>();

  @ElementCollection
  @CollectionTable(name = "question_topic", joinColumns = @JoinColumn(name = "question_id"))
  private Set<TopicLink> topics = new HashSet<>();

  protected Question() {
    // For JPA.
  }

  Question(UUID ownerId) {
    this.ownerId = ownerId;
  }

  /** Replaces everything the client edits; input already passed QuestionRules. */
  void apply(QuestionInput input) {
    type = input.type();
    promptMd = input.promptMd();
    answerMd = blankToNull(input.answerMd());
    explanationMd = blankToNull(input.explanationMd());
    difficulty = input.difficulty().shortValue();
    sourceUrl = blankToNull(input.sourceUrl());
    primaryTopicId = input.primaryTopicId();

    // Only the difference is written: unchanged links keep their rows.
    Set<TopicLink> wanted =
        input.topicIdsOrEmpty().stream()
            .map(topicId -> new TopicLink(topicId, ownerId))
            .collect(Collectors.toSet());
    topics.retainAll(wanted);
    topics.addAll(wanted);

    // Options are matched by position: an option keeps its id while it keeps its place, so later
    // modules (quiz attempts) can refer to it. Extra old ones go, extra new ones come.
    List<QuestionInput.OptionInput> wantedOptions = input.optionsOrEmpty();
    for (int i = 0; i < wantedOptions.size(); i++) {
      QuestionInput.OptionInput option = wantedOptions.get(i);
      if (i < options.size()) {
        options.get(i).change(option.textMd(), option.correct());
      } else {
        options.add(new AnswerOption(this, option.textMd(), option.correct(), (short) i));
      }
    }
    while (options.size() > wantedOptions.size()) {
      options.removeLast();
    }
  }

  void archive(Instant now) {
    if (archivedAt == null) {
      archivedAt = now;
    }
  }

  void unarchive() {
    archivedAt = null;
  }

  private static String blankToNull(String text) {
    return QuestionRules.isBlank(text) ? null : text;
  }

  UUID getId() {
    return id;
  }

  UUID getOwnerId() {
    return ownerId;
  }

  QuestionType getType() {
    return type;
  }

  String getPromptMd() {
    return promptMd;
  }

  String getAnswerMd() {
    return answerMd;
  }

  String getExplanationMd() {
    return explanationMd;
  }

  int getDifficulty() {
    return difficulty;
  }

  QuestionStatus getStatus() {
    return status;
  }

  String getSourceUrl() {
    return sourceUrl;
  }

  UUID getPrimaryTopicId() {
    return primaryTopicId;
  }

  Instant getFlaggedAt() {
    return flaggedAt;
  }

  String getFlagNote() {
    return flagNote;
  }

  Instant getArchivedAt() {
    return archivedAt;
  }

  long getVersion() {
    return version;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }

  List<AnswerOption> getOptions() {
    return options;
  }

  Set<UUID> getTopicIds() {
    return topics.stream().map(TopicLink::getTopicId).collect(Collectors.toSet());
  }
}
