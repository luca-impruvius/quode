package com.quode.question.internal;

import com.quode.shared.NotFoundException;
import com.quode.taxonomy.TopicLookup;
import com.quode.taxonomy.TopicRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Export in the import format (M2): the owner's non-archived questions of one status. */
@Service
class ExportService {

  private final QuestionRepository questions;
  private final TopicLookup topics;

  ExportService(QuestionRepository questions, TopicLookup topics) {
    this.questions = questions;
    this.topics = topics;
  }

  /** {@code topicId}: only questions with a topic in its subtree, like the Library filter. */
  static final int MAX_QUESTIONS = ImportService.MAX_QUESTIONS;

  @Transactional(readOnly = true)
  ExportFile export(UUID ownerId, QuestionStatus status, UUID topicId) {
    Set<UUID> subtree =
        topicId == null
            ? null
            // Another user's topic looks like a missing one.
            : topics
                .subtreeIds(ownerId, topicId)
                .orElseThrow(() -> new NotFoundException(QuestionService.TOPIC_NOT_FOUND));
    List<Question> found =
        questions.findForExport(ownerId, status).stream()
            .filter(q -> subtree == null || q.getTopicIds().stream().anyMatch(subtree::contains))
            .toList();
    // One export is at most one import: filled and imported, the next export gives the rest.
    List<Question> shown = found.subList(0, Math.min(found.size(), MAX_QUESTIONS));
    Set<UUID> topicIds = new HashSet<>();
    shown.forEach(q -> topicIds.addAll(q.getTopicIds()));
    Map<UUID, TopicRef> paths = topics.findOwned(ownerId, topicIds);
    return new ExportFile(
        null, shown.stream().map(q -> item(q, paths)).toList(), found.size() - shown.size());
  }

  private static ExportFile.ExportItem item(Question q, Map<UUID, TopicRef> paths) {
    List<String> topicPaths = new ArrayList<>();
    topicPaths.add(paths.get(q.getPrimaryTopicId()).path());
    q.getTopicIds().stream()
        .filter(id -> !id.equals(q.getPrimaryTopicId()))
        .map(id -> paths.get(id).path())
        .sorted()
        .forEach(topicPaths::add);
    return new ExportFile.ExportItem(
        q.getId(),
        q.getType(),
        q.getPromptMd(),
        q.getAnswerMd(),
        q.getOptions().stream()
            .map(o -> new ExportFile.ExportOption(o.getTextMd(), o.isCorrect()))
            .toList(),
        q.getExplanationMd(),
        q.getDifficulty(),
        q.getSourceUrl(),
        topicPaths);
  }
}
