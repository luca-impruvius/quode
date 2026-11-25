package com.quode.study.internal;

import com.quode.question.QuestionCatalog;
import com.quode.question.QuestionCatalog.StudyQuestion;
import com.quode.shared.ConflictException;
import com.quode.shared.NotFoundException;
import com.quode.study.AttemptRecorded;
import com.quode.study.internal.StudyDtos.AnswerInput;
import com.quode.study.internal.StudyDtos.AnswerResult;
import com.quode.study.internal.StudyDtos.CardOption;
import com.quode.study.internal.StudyDtos.FinishResult;
import com.quode.study.internal.StudyDtos.MissedQuestion;
import com.quode.study.internal.StudyDtos.QuestionCard;
import com.quode.study.internal.StudyDtos.SessionView;
import com.quode.study.internal.StudyDtos.StartSession;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Quick quizzes (docs/specs/M2-exam-prep-mvp.md): an ADHOC session snapshots its question order,
 * takes one answer per question in that order, and can be resumed. Every method is per owner.
 */
@Service
class StudyService {

  static final int MAX_QUESTIONS = 2_000;
  static final int MAX_TOPICS = 100;

  static final String NOT_FOUND = "Study session not found.";
  static final String BAD_START =
      "Start a quiz with 1–100 topicIds or questionIds, count 1–2,000 or none for all (up to"
          + " 2,000), mode ALL, UNSEEN or MISSED.";
  static final String NONE_MATCH = "No questions to study here yet.";
  static final String OUT_OF_ORDER =
      "This isn't the question the quiz is on. Reload to continue where it is.";
  static final String FINISHED = "This quiz is finished.";
  static final String CHOICE_NEEDS_OPTIONS = "Choose your options; choice questions are graded.";
  static final String UNKNOWN_OPTION = "One of the chosen options isn't part of this question.";
  static final String NEEDS_GRADE = "Grade yourself: GOT_IT, PARTIAL or MISSED.";

  enum Mode {
    ALL,
    UNSEEN,
    MISSED
  }

  private record Session(
      UUID id, String kind, List<UUID> order, int currentIndex, Instant finishedAt) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final QuestionCatalog catalog;
  private final ApplicationEventPublisher events;
  private final RandomGenerator random;

  StudyService(
      NamedParameterJdbcTemplate jdbc, QuestionCatalog catalog, ApplicationEventPublisher events) {
    this.jdbc = jdbc;
    this.catalog = catalog;
    this.events = events;
    this.random = RandomGenerator.getDefault();
  }

  @Transactional
  SessionView start(UUID ownerId, StartSession input) {
    Mode mode = parse(input.mode() == null ? "ALL" : input.mode());
    boolean fromQuestions = input.questionIds() != null && !input.questionIds().isEmpty();
    boolean fromTopics = input.topicIds() != null && !input.topicIds().isEmpty();
    if ((input.kind() != null && !"ADHOC".equalsIgnoreCase(input.kind()))
        || mode == null
        || fromQuestions == fromTopics
        || (input.count() != null && (input.count() < 1 || input.count() > MAX_QUESTIONS))
        || (fromQuestions && input.questionIds().size() > MAX_QUESTIONS)
        || (fromTopics && input.topicIds().size() > MAX_TOPICS)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, BAD_START);
    }
    List<UUID> candidates =
        new ArrayList<>(
            fromQuestions
                ? catalog.keepStudyable(ownerId, input.questionIds())
                : catalog.studyableIds(ownerId, input.topicIds()));
    if (mode != Mode.ALL) {
      Set<UUID> keep = filterBy(ownerId, candidates, mode);
      candidates.removeIf(id -> !keep.contains(id));
    }
    if (Boolean.TRUE.equals(input.shuffle())) {
      Collections.shuffle(candidates, random);
    }
    int count = input.count() == null ? MAX_QUESTIONS : input.count();
    List<UUID> order = candidates.subList(0, Math.min(count, candidates.size()));
    if (order.isEmpty()) {
      throw new ConflictException(NONE_MATCH);
    }
    // A real uuid[] (named parameters would expand an array into a list of values).
    UUID id =
        jdbc.getJdbcTemplate()
            .execute(
                (ConnectionCallback<UUID>)
                    con -> {
                      try (PreparedStatement ps =
                          con.prepareStatement(
                              "insert into study_session (owner_id, kind, question_order)"
                                  + " values (?, 'ADHOC', ?) returning id")) {
                        ps.setObject(1, ownerId);
                        ps.setArray(2, con.createArrayOf("uuid", order.toArray()));
                        try (ResultSet rs = ps.executeQuery()) {
                          rs.next();
                          return rs.getObject(1, UUID.class);
                        }
                      }
                    });
    return view(ownerId, find(ownerId, id, false));
  }

  @Transactional(readOnly = true)
  SessionView get(UUID ownerId, UUID id) {
    return view(ownerId, find(ownerId, id, false));
  }

  @Transactional
  AnswerResult answer(UUID ownerId, UUID id, AnswerInput input) {
    Session session = find(ownerId, id, true);
    if (session.finishedAt() != null) {
      throw new ConflictException(FINISHED);
    }
    if (session.currentIndex() >= session.order().size()
        || input.questionId() == null
        || !session.order().get(session.currentIndex()).equals(input.questionId())) {
      throw new ConflictException(OUT_OF_ORDER);
    }
    StudyQuestion question =
        catalog.find(ownerId, List.of(input.questionId())).get(input.questionId());
    if (question == null) {
      throw new NotFoundException(NOT_FOUND);
    }
    Graded graded = grade(question, input);
    jdbc.update(
        "insert into attempt (session_id, owner_id, question_id, outcome, given_answer, graded_by)"
            + " values (:session, :owner, :question, :outcome, :given, :by)",
        new MapSqlParameterSource("session", id)
            .addValue("owner", ownerId)
            .addValue("question", question.id())
            .addValue("outcome", graded.outcome())
            .addValue("given", graded.given())
            .addValue("by", graded.by()));
    jdbc.update(
        "update study_session set current_index = current_index + 1 where id = :id",
        new MapSqlParameterSource("id", id));
    events.publishEvent(
        new AttemptRecorded(ownerId, id, session.kind(), question.id(), graded.outcome()));
    List<UUID> correct =
        question.options().stream()
            .filter(StudyQuestion.Option::correct)
            .map(StudyQuestion.Option::id)
            .toList();
    return new AnswerResult(
        graded.outcome(),
        correct,
        question.answerMd(),
        question.explanationMd(),
        view(ownerId, find(ownerId, id, false)));
  }

  @Transactional
  FinishResult finish(UUID ownerId, UUID id) {
    Session session = find(ownerId, id, true);
    if (session.finishedAt() == null) {
      jdbc.update(
          "update study_session set finished_at = now() where id = :id",
          new MapSqlParameterSource("id", id));
    }
    Map<UUID, String> outcomes = outcomes(id);
    int correct = 0;
    int partial = 0;
    int wrong = 0;
    List<UUID> missed = new ArrayList<>();
    for (UUID question : session.order()) {
      String outcome = outcomes.get(question);
      if (outcome == null) {
        continue;
      }
      switch (outcome) {
        case "CORRECT" -> correct++;
        case "PARTIAL" -> partial++;
        default -> wrong++;
      }
      if (!"CORRECT".equals(outcome)) {
        missed.add(question);
      }
    }
    Map<UUID, StudyQuestion> prompts = catalog.find(ownerId, missed);
    List<MissedQuestion> missedList =
        missed.stream()
            .filter(prompts::containsKey)
            .map(q -> new MissedQuestion(q, prompts.get(q).promptMd()))
            .toList();
    return new FinishResult(
        session.order().size(), outcomes.size(), correct, partial, wrong, missed, missedList);
  }

  private record Graded(String outcome, String given, String by) {}

  // Choice questions are graded at once (MULTIPLE_CHOICE all-or-nothing); the others self-graded.
  private static Graded grade(StudyQuestion question, AnswerInput input) {
    if (question.type().isChoice()) {
      if (input.selectedOptionIds() == null || input.grade() != null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, CHOICE_NEEDS_OPTIONS);
      }
      Set<UUID> chosen = new HashSet<>(input.selectedOptionIds());
      Set<UUID> all = new HashSet<>();
      Set<UUID> right = new HashSet<>();
      question
          .options()
          .forEach(
              o -> {
                all.add(o.id());
                if (o.correct()) {
                  right.add(o.id());
                }
              });
      if (!all.containsAll(chosen)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, UNKNOWN_OPTION);
      }
      String given =
          chosen.stream().map(u -> "\"" + u + "\"").sorted().toList().toString().replace(" ", "");
      // A question edited back to having no correct option can't be answered right.
      boolean correct = !right.isEmpty() && chosen.equals(right);
      return new Graded(correct ? "CORRECT" : "WRONG", given, "AUTO");
    }
    String grade = input.grade() == null ? "" : input.grade().toUpperCase(Locale.ROOT);
    String outcome =
        switch (grade) {
          case "GOT_IT" -> "CORRECT";
          case "PARTIAL" -> "PARTIAL";
          case "MISSED" -> "WRONG";
          default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NEEDS_GRADE);
        };
    if (input.selectedOptionIds() != null && !input.selectedOptionIds().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NEEDS_GRADE);
    }
    return new Graded(outcome, "\"" + grade + "\"", "SELF");
  }

  // UNSEEN: never answered by this user. MISSED: the latest answer was wrong or partial.
  private Set<UUID> filterBy(UUID ownerId, List<UUID> candidates, Mode mode) {
    if (candidates.isEmpty()) {
      return Set.of();
    }
    List<Map<String, Object>> latest =
        jdbc.queryForList(
            "select distinct on (question_id) question_id, outcome from attempt"
                + " where owner_id = :owner and question_id in (:ids)"
                + " order by question_id, answered_at desc, id desc",
            new MapSqlParameterSource("owner", ownerId).addValue("ids", candidates));
    Set<UUID> seen = new HashSet<>();
    Set<UUID> missed = new HashSet<>();
    for (Map<String, Object> row : latest) {
      UUID question = (UUID) row.get("question_id");
      seen.add(question);
      if (!"CORRECT".equals(row.get("outcome"))) {
        missed.add(question);
      }
    }
    if (mode == Mode.MISSED) {
      return missed;
    }
    Set<UUID> unseen = new HashSet<>(candidates);
    unseen.removeAll(seen);
    return unseen;
  }

  private Map<UUID, String> outcomes(UUID sessionId) {
    Map<UUID, String> outcomes = new java.util.HashMap<>();
    jdbc.query(
        "select question_id, outcome from attempt where session_id = :id",
        new MapSqlParameterSource("id", sessionId),
        (ResultSet rs) -> {
          outcomes.put(rs.getObject("question_id", UUID.class), rs.getString("outcome"));
        });
    return outcomes;
  }

  // Another user's session looks like a missing one. {@code lock}: until commit, so two answers to
  // the same question can't both be counted.
  private Session find(UUID ownerId, UUID id, boolean lock) {
    List<Session> found =
        jdbc.query(
            "select id, kind, question_order, current_index, finished_at from study_session"
                + " where id = :id and owner_id = :owner"
                + (lock ? " for update" : ""),
            new MapSqlParameterSource("id", id).addValue("owner", ownerId),
            (rs, n) -> session(rs));
    if (found.isEmpty()) {
      throw new NotFoundException(NOT_FOUND);
    }
    return found.getFirst();
  }

  private static Session session(ResultSet rs) throws SQLException {
    Array array = rs.getArray("question_order");
    List<UUID> order = List.of((UUID[]) array.getArray());
    Timestamp finished = rs.getTimestamp("finished_at");
    return new Session(
        rs.getObject("id", UUID.class),
        rs.getString("kind"),
        order,
        rs.getInt("current_index"),
        finished == null ? null : finished.toInstant());
  }

  private SessionView view(UUID ownerId, Session session) {
    Map<UUID, String> outcomes = outcomes(session.id());
    int correct = (int) outcomes.values().stream().filter("CORRECT"::equals).count();
    QuestionCard current = null;
    if (session.currentIndex() < session.order().size()) {
      UUID next = session.order().get(session.currentIndex());
      StudyQuestion q = catalog.find(ownerId, List.of(next)).get(next);
      if (q != null) {
        current =
            new QuestionCard(
                q.id(),
                q.type(),
                q.promptMd(),
                q.options().stream().map(o -> new CardOption(o.id(), o.textMd())).toList(),
                q.type().isChoice() ? null : q.answerMd(),
                q.type().isChoice() ? null : q.explanationMd());
      }
    }
    return new SessionView(
        session.id(),
        session.kind(),
        session.order().size(),
        outcomes.size(),
        correct,
        current,
        session.finishedAt());
  }

  private static Mode parse(String mode) {
    for (Mode m : Mode.values()) {
      if (m.name().equalsIgnoreCase(mode)) {
        return m;
      }
    }
    return null;
  }
}
