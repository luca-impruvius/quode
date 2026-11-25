package com.quode.question.internal;

import com.quode.question.QuestionCreated;
import com.quode.question.internal.ImportInput.ImportItem;
import com.quode.question.internal.ImportReport.ImportProblem;
import com.quode.question.internal.QuestionInput.OptionInput;
import com.quode.taxonomy.TopicPaths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.server.ResponseStatusException;

/**
 * Bulk import (docs/specs/M2-exam-prep-mvp.md): one transaction per import. Invalid questions are
 * reported and left out; the rest are saved. A dry run does all of it, then rolls back, so its
 * report is exactly what the real import would do.
 */
@Service
class ImportService {

  static final int MAX_QUESTIONS = 2_000;
  static final int DEFAULT_DIFFICULTY = 2;

  static final String BAD_SIZE = "An import needs 1 to 2,000 questions.";
  static final String EMPTY = "This entry is empty.";
  static final String NO_TOPIC =
      "Give the question a topic: the file's \"topic\" or the question's own \"topics\".";
  static final String BAD_ID = "This id is not a question id.";
  static final String NOT_FOUND = "No draft question with this id.";
  static final String ALREADY_ACTIVE = "This question already has an answer; it was not changed.";
  static final String STILL_NO_ANSWER =
      "Still no answer: mark a correct option or give the answer text.";

  private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

  private final QuestionRepository questions;
  private final TopicPaths topicPaths;
  private final ApplicationEventPublisher events;

  ImportService(
      QuestionRepository questions, TopicPaths topicPaths, ApplicationEventPublisher events) {
    this.questions = questions;
    this.topicPaths = topicPaths;
    this.events = events;
  }

  @Transactional
  ImportReport importQuestions(UUID ownerId, ImportInput input, boolean dryRun) {
    List<ImportItem> items = input == null ? null : input.questions();
    if (items == null || items.isEmpty() || items.size() > MAX_QUESTIONS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, BAD_SIZE);
    }
    Run run = new Run(ownerId, input.topic(), items);
    for (int i = 0; i < items.size(); i++) {
      ImportItem item = items.get(i);
      Optional<String> problem =
          item == null ? Optional.of(EMPTY) : isFill(item) ? run.fill(item) : run.create(item);
      int position = i + 1;
      problem.ifPresent(reason -> run.problems.add(new ImportProblem(position, reason)));
    }
    questions.flush();
    // After the flush, so the rows exist for the review module's enrolment (a dry run rolls it
    // back).
    run.saved.forEach(q -> events.publishEvent(new QuestionCreated(ownerId, q.getId())));
    if (dryRun) {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
    return new ImportReport(
        run.created,
        run.filled,
        run.drafts,
        run.skippedDuplicates,
        run.topics.created(),
        List.copyOf(run.problems));
  }

  private static boolean isFill(ImportItem item) {
    return !QuestionRules.isBlank(item.id());
  }

  /** Prompts compare ignoring case and repeated spaces (non-breaking ones included). */
  static String normalize(String prompt) {
    return SPACES.matcher(prompt.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
  }

  /** The state of one import. */
  private final class Run {

    private final UUID ownerId;
    private final String fileTopic;
    private final TopicPaths.Resolved topics;
    private final Set<String> seenPrompts = new HashSet<>();
    private final Map<UUID, Question> toFill = new HashMap<>();
    private final List<ImportProblem> problems = new ArrayList<>();
    private final List<Question> saved = new ArrayList<>();
    private int created;
    private int filled;
    private int drafts;
    private int skippedDuplicates;

    Run(UUID ownerId, String fileTopic, List<ImportItem> items) {
      this.ownerId = ownerId;
      this.fileTopic = QuestionRules.isBlank(fileTopic) ? null : fileTopic;
      Set<String> paths = new LinkedHashSet<>();
      List<ImportItem> newOnes = items.stream().filter(i -> i != null && !isFill(i)).toList();
      // A fill-only file (an export with answers) creates no topics, not even its own one.
      if (this.fileTopic != null && !newOnes.isEmpty()) {
        paths.add(this.fileTopic);
      }
      for (ImportItem item : newOnes) {
        if (item.topics() != null) {
          item.topics().stream().filter(p -> !QuestionRules.isBlank(p)).forEach(paths::add);
        }
      }
      // Topics of questions that turn out invalid are still created: the import is one batch.
      this.topics = topicPaths.resolve(ownerId, paths);
      questions.findPrompts(ownerId).forEach(p -> seenPrompts.add(normalize(p)));
      // Questions to fill, in one locking query before anything is saved (no N+1, no flushes).
      List<UUID> ids =
          items.stream()
              .filter(i -> i != null && isFill(i))
              .map(i -> parseId(i.id()))
              .filter(Objects::nonNull)
              .distinct()
              .toList();
      if (!ids.isEmpty()) {
        questions.findAllForUpdate(ids, ownerId).forEach(q -> toFill.put(q.getId(), q));
      }
    }

    Optional<String> create(ImportItem item) {
      QuestionType type = parseType(item.type());
      if (type == null) {
        return Optional.of(QuestionRules.TYPE);
      }
      if (QuestionRules.isBlank(item.prompt())) {
        return Optional.of(QuestionRules.PROMPT);
      }
      if (seenPrompts.contains(normalize(item.prompt()))) {
        skippedDuplicates++;
        return Optional.empty();
      }
      List<String> paths = new ArrayList<>();
      if (fileTopic != null) {
        paths.add(fileTopic);
      }
      if (item.topics() != null) {
        item.topics().stream().filter(p -> !QuestionRules.isBlank(p)).forEach(paths::add);
      }
      List<UUID> topicIds = new ArrayList<>();
      for (String path : paths) {
        String bad = topics.problems().get(path);
        if (bad != null) {
          return Optional.of(bad + " (" + path.strip() + ")");
        }
        UUID id = topics.ids().get(path);
        if (!topicIds.contains(id)) {
          topicIds.add(id);
        }
      }
      if (topicIds.isEmpty()) {
        return Optional.of(NO_TOPIC);
      }
      QuestionInput input =
          new QuestionInput(
              type,
              item.prompt(),
              item.answer(),
              item.explanation(),
              item.difficulty() == null ? DEFAULT_DIFFICULTY : item.difficulty(),
              item.sourceUrl(),
              topicIds,
              topicIds.getFirst(),
              options(item.options()),
              null);
      boolean draft = QuestionRules.needsAnswer(input);
      Optional<String> broken =
          draft ? QuestionRules.checkDraft(input) : QuestionRules.check(input);
      if (broken.isPresent()) {
        return broken;
      }
      Question question = new Question(ownerId);
      question.apply(input);
      if (draft) {
        question.markDraft();
        drafts++;
      } else {
        created++;
      }
      saved.add(questions.save(question));
      seenPrompts.add(normalize(item.prompt()));
      return Optional.empty();
    }

    /** Export and fill: completes a DRAFT question; an ACTIVE one is never overwritten. */
    Optional<String> fill(ImportItem item) {
      UUID id = parseId(item.id());
      if (id == null) {
        return Optional.of(BAD_ID);
      }
      Question question = toFill.get(id);
      if (question == null) {
        return Optional.of(NOT_FOUND);
      }
      if (question.getStatus() == QuestionStatus.ACTIVE) {
        return Optional.of(ALREADY_ACTIVE);
      }
      List<OptionInput> options =
          item.options() == null || item.options().isEmpty()
              ? question.getOptions().stream()
                  .map(o -> new OptionInput(o.getTextMd(), o.isCorrect()))
                  .toList()
              : options(item.options());
      List<UUID> topicIds = new ArrayList<>();
      topicIds.add(question.getPrimaryTopicId());
      question.getTopicIds().stream().filter(t -> !topicIds.contains(t)).forEach(topicIds::add);
      QuestionInput input =
          new QuestionInput(
              question.getType(),
              question.getPromptMd(),
              item.answer() == null ? question.getAnswerMd() : item.answer(),
              item.explanation() == null ? question.getExplanationMd() : item.explanation(),
              item.difficulty() == null ? question.getDifficulty() : item.difficulty(),
              item.sourceUrl() == null ? question.getSourceUrl() : item.sourceUrl(),
              topicIds,
              question.getPrimaryTopicId(),
              options,
              null);
      if (QuestionRules.needsAnswer(input)) {
        return Optional.of(STILL_NO_ANSWER);
      }
      Optional<String> broken = QuestionRules.check(input);
      if (broken.isPresent()) {
        return broken;
      }
      question.apply(input);
      question.activate();
      filled++;
      return Optional.empty();
    }
  }

  private static List<OptionInput> options(List<ImportInput.ImportOption> options) {
    if (options == null) {
      return List.of();
    }
    // A null entry stays null, so the option rules report it.
    return options.stream()
        .map(o -> o == null ? null : new OptionInput(o.text(), Boolean.TRUE.equals(o.correct())))
        .toList();
  }

  private static UUID parseId(String id) {
    try {
      return UUID.fromString(id.strip());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static QuestionType parseType(String type) {
    if (type == null) {
      return null;
    }
    for (QuestionType t : QuestionType.values()) {
      if (t.name().equalsIgnoreCase(type.strip())) {
        return t;
      }
    }
    return null;
  }
}
