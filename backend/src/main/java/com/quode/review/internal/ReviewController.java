package com.quode.review.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quode.question.QuestionCatalog;
import com.quode.shared.NotFoundException;
import com.quode.user.CurrentUser;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Today's counts, and a question's review controls: Learn next and Remove from review / Add to
 * review (docs/specs/M3-daily-review.md, API).
 */
@RestController
@RequestMapping("/v1")
class ReviewController {

  static final String NOT_FOUND = "Question not found.";
  static final String BAD_CONTROLS = "Send enrolled and learnNext, both true or false.";

  /**
   * Today: {@code due} and {@code newWaiting} are what a review started now would ask (within
   * today's limits); {@code moreDue} and {@code moreNew} are waiting beyond them, for Keep going
   * and Learn more. {@code nextDueAt} is the next due time after today; {@code sessionId} today's
   * unfinished review.
   */
  record ReviewToday(
      int due,
      int newWaiting,
      int moreDue,
      int moreNew,
      int doneToday,
      int newToday,
      @Schema(types = {"string", "null"}) Instant nextDueAt,
      @Schema(types = {"string", "null"}) UUID sessionId) {}

  /** A question in review: {@code dueAt} null while new or not enrolled. */
  record QuestionReview(
      boolean enrolled,
      boolean learnNext,
      @Schema(types = {"string", "null"}) Instant dueAt,
      @JsonProperty("isNew") boolean isNew) {}

  /** {@code enrolled} false removes the question from review; true adds it back as new. */
  record ReviewControls(Boolean enrolled, Boolean learnNext) {}

  private final ReviewQueue queue;
  private final ReviewStates states;
  private final QuestionCatalog catalog;
  private final CurrentUser currentUser;

  ReviewController(
      ReviewQueue queue, ReviewStates states, QuestionCatalog catalog, CurrentUser currentUser) {
    this.queue = queue;
    this.states = states;
    this.catalog = catalog;
    this.currentUser = currentUser;
  }

  @GetMapping("/review/today")
  ReviewToday getReviewToday() {
    ReviewQueue.Today t = queue.today(currentUser.id());
    return new ReviewToday(
        t.due(),
        t.newWaiting(),
        t.moreDue(),
        t.moreNew(),
        t.doneToday(),
        t.newToday(),
        t.nextDueAt(),
        t.sessionId());
  }

  @GetMapping("/questions/{id}/review")
  QuestionReview getQuestionReview(@PathVariable UUID id) {
    UUID me = currentUser.id();
    requireOwn(me, id);
    return view(me, id);
  }

  @PutMapping("/questions/{id}/review")
  QuestionReview saveQuestionReview(@PathVariable UUID id, @RequestBody ReviewControls input) {
    if (input.enrolled() == null || input.learnNext() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, BAD_CONTROLS);
    }
    UUID me = currentUser.id();
    requireOwn(me, id);
    if (input.enrolled()) {
      states.enrolWithLearnNext(me, id, input.learnNext());
    } else {
      states.remove(me, id);
    }
    return view(me, id);
  }

  // Unknown ids and other users' questions look the same: not found.
  private void requireOwn(UUID ownerId, UUID questionId) {
    if (catalog.find(ownerId, List.of(questionId)).isEmpty()) {
      throw new NotFoundException(NOT_FOUND);
    }
  }

  private QuestionReview view(UUID ownerId, UUID questionId) {
    return states
        .find(ownerId, questionId, false)
        .map(s -> new QuestionReview(true, s.learnNext(), s.dueAt(), s.isNew()))
        .orElse(new QuestionReview(false, false, null, false));
  }
}
