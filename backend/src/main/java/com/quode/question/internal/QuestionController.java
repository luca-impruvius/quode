package com.quode.question.internal;

import com.quode.user.CurrentUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The current user's questions. Rules: docs/specs/M1-questions.md. */
@RestController
@RequestMapping("/v1/questions")
class QuestionController {

  /** The version the client read, for archive and unarchive. */
  record VersionInput(Long version) {}

  static final String BAD_LIST =
      "Check the Library filters: page ≥ 0, size 1–50, difficulty 1–3, a known sort and status, search up to 200 characters.";

  private static final int MAX_PAGE_SIZE = 50;
  private static final int MAX_SEARCH = 200;

  private final QuestionService questions;
  private final LibraryService library;
  private final CurrentUser currentUser;

  QuestionController(QuestionService questions, LibraryService library, CurrentUser currentUser) {
    this.questions = questions;
    this.library = library;
    this.currentUser = currentUser;
  }

  /** The Library list (docs/specs/M1-library.md). */
  @GetMapping
  LibraryPage list(
      @RequestParam(required = false) UUID topicId,
      @RequestParam(required = false) QuestionType type,
      @RequestParam(required = false) Integer difficulty,
      @RequestParam(defaultValue = "ACTIVE") String status,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "created") String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    String text = q == null || q.isBlank() ? null : q.strip();
    LibraryQuery.Status statusValue = parse(LibraryQuery.Status.class, status);
    LibraryQuery.Sort sortValue = parse(LibraryQuery.Sort.class, sort);
    if (page < 0
        || size < 1
        || size > MAX_PAGE_SIZE
        || (difficulty != null && (difficulty < 1 || difficulty > 3))
        || statusValue == null
        || sortValue == null
        || (text != null && text.length() > MAX_SEARCH)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, BAD_LIST);
    }
    return library.list(
        currentUser.id(),
        new LibraryQuery(topicId, type, difficulty, statusValue, text, sortValue, page, size));
  }

  // Case-insensitive enum lookup; null when unknown.
  private static <E extends Enum<E>> E parse(Class<E> type, String value) {
    for (E constant : type.getEnumConstants()) {
      if (constant.name().equalsIgnoreCase(value)) {
        return constant;
      }
    }
    return null;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  QuestionView create(@RequestBody QuestionInput input) {
    return questions.create(currentUser.id(), input);
  }

  @GetMapping("/{id}")
  QuestionView get(@PathVariable UUID id) {
    return questions.get(currentUser.id(), id);
  }

  @PutMapping("/{id}")
  QuestionView update(@PathVariable UUID id, @RequestBody QuestionInput input) {
    return questions.update(currentUser.id(), id, input);
  }

  @PostMapping("/{id}/archive")
  QuestionView archive(@PathVariable UUID id, @RequestBody VersionInput input) {
    return questions.archive(currentUser.id(), id, input.version());
  }

  @PostMapping("/{id}/unarchive")
  QuestionView unarchive(@PathVariable UUID id, @RequestBody VersionInput input) {
    return questions.unarchive(currentUser.id(), id, input.version());
  }
}
