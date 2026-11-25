package com.quode.study.internal;

import com.quode.study.internal.StudyDtos.AnswerInput;
import com.quode.study.internal.StudyDtos.AnswerResult;
import com.quode.study.internal.StudyDtos.FinishResult;
import com.quode.study.internal.StudyDtos.SessionView;
import com.quode.study.internal.StudyDtos.StartSession;
import com.quode.user.CurrentUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Quick quizzes as study sessions (docs/specs/M2-exam-prep-mvp.md). */
@RestController
@RequestMapping("/v1/study/sessions")
class StudyController {

  private final StudyService study;
  private final CurrentUser currentUser;

  StudyController(StudyService study, CurrentUser currentUser) {
    this.study = study;
    this.currentUser = currentUser;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  SessionView startSession(@RequestBody StartSession input) {
    return study.start(currentUser.id(), input);
  }

  @GetMapping("/{id}")
  SessionView getSession(@PathVariable UUID id) {
    return study.get(currentUser.id(), id);
  }

  @PostMapping("/{id}/answers")
  AnswerResult answerQuestion(@PathVariable UUID id, @RequestBody AnswerInput input) {
    return study.answer(currentUser.id(), id, input);
  }

  @PostMapping("/{id}/finish")
  FinishResult finishSession(@PathVariable UUID id) {
    return study.finish(currentUser.id(), id);
  }
}
