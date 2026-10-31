package com.quode.question.internal;

import com.quode.shared.ConflictException;
import com.quode.shared.NotFoundException;
import com.quode.taxonomy.TopicLookup;
import com.quode.taxonomy.TopicRef;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Create, read, edit, archive (docs/specs/M1-questions.md). Every method is per owner. */
@Service
class QuestionService {

  static final String NOT_FOUND = "Question not found.";
  static final String TOPIC_NOT_FOUND = "Topic not found.";
  static final String STALE = "This question changed elsewhere. Reload to see the latest version.";
  static final String VERSION_REQUIRED = "Send the version of the question you edited.";

  private final QuestionRepository questions;
  private final TopicLookup topics;

  QuestionService(QuestionRepository questions, TopicLookup topics) {
    this.questions = questions;
    this.topics = topics;
  }

  @Transactional
  QuestionView create(UUID ownerId, QuestionInput input) {
    requireValid(ownerId, input);
    Question question = new Question(ownerId);
    question.apply(input);
    return view(questions.saveAndFlush(question));
  }

  @Transactional(readOnly = true)
  QuestionView get(UUID ownerId, UUID id) {
    return view(find(ownerId, id));
  }

  @Transactional
  QuestionView update(UUID ownerId, UUID id, QuestionInput input) {
    Question question = findAtVersion(ownerId, id, input.version());
    requireValid(ownerId, input);
    question.apply(input);
    return view(save(question));
  }

  @Transactional
  QuestionView archive(UUID ownerId, UUID id, Long version) {
    Question question = findAtVersion(ownerId, id, version);
    question.archive(Instant.now());
    return view(save(question));
  }

  @Transactional
  QuestionView unarchive(UUID ownerId, UUID id, Long version) {
    Question question = findAtVersion(ownerId, id, version);
    question.unarchive();
    return view(save(question));
  }

  private void requireValid(UUID ownerId, QuestionInput input) {
    QuestionRules.check(input)
        .ifPresent(
            message -> {
              throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
            });
    // Unknown ids and other users' topics look the same: not found.
    if (topics.findOwned(ownerId, input.topicIds()).size() != input.topicIds().size()) {
      throw new NotFoundException(TOPIC_NOT_FOUND);
    }
  }

  private Question find(UUID ownerId, UUID id) {
    return questions
        .findByIdAndOwnerId(id, ownerId)
        .orElseThrow(() -> new NotFoundException(NOT_FOUND));
  }

  // The client must name the version it read; an older one means someone saved in between.
  private Question findAtVersion(UUID ownerId, UUID id, Long version) {
    Question question =
        questions.findForUpdate(id, ownerId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
    if (version == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, VERSION_REQUIRED);
    }
    if (version != question.getVersion()) {
      throw new ConflictException(STALE);
    }
    return question;
  }

  // Backstop: the row lock already serializes edits, but a version clash still maps to the same
  // 409.
  private Question save(Question question) {
    try {
      return questions.saveAndFlush(question);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException(STALE);
    }
  }

  private QuestionView view(Question q) {
    Map<UUID, TopicRef> refs = topics.findOwned(q.getOwnerId(), q.getTopicIds());
    List<QuestionView.TopicItem> topicItems =
        refs.values().stream()
            .map(
                ref ->
                    new QuestionView.TopicItem(
                        ref.id(), ref.name(), ref.path(), ref.id().equals(q.getPrimaryTopicId())))
            .sorted(
                Comparator.comparing((QuestionView.TopicItem t) -> !t.primary())
                    .thenComparing(QuestionView.TopicItem::path))
            .toList();
    List<QuestionView.OptionItem> optionItems =
        q.getOptions().stream()
            .map(o -> new QuestionView.OptionItem(o.getId(), o.getTextMd(), o.isCorrect()))
            .toList();
    return new QuestionView(
        q.getId(),
        q.getType(),
        q.getPromptMd(),
        q.getAnswerMd(),
        q.getExplanationMd(),
        q.getDifficulty(),
        q.getStatus(),
        q.getSourceUrl(),
        topicItems,
        optionItems,
        q.getFlaggedAt(),
        q.getFlagNote(),
        q.getArchivedAt(),
        q.getVersion(),
        q.getCreatedAt(),
        q.getUpdatedAt());
  }
}
