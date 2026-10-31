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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The current user's questions. Rules: docs/specs/M1-questions.md. */
@RestController
@RequestMapping("/v1/questions")
class QuestionController {

  /** The version the client read, for archive and unarchive. */
  record VersionInput(Long version) {}

  private final QuestionService questions;
  private final CurrentUser currentUser;

  QuestionController(QuestionService questions, CurrentUser currentUser) {
    this.questions = questions;
    this.currentUser = currentUser;
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
