package com.quode.study.internal;

import com.quode.study.internal.StudyDtos.AnswerInput;
import com.quode.study.internal.StudyDtos.AnswerResult;
import com.quode.study.internal.StudyDtos.FinishResult;
import com.quode.study.internal.StudyDtos.SessionView;
import com.quode.study.internal.StudyDtos.StartSession;
import com.quode.user.CurrentUser;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quick quizzes and daily reviews as study sessions (M2 and M3 specs). */
@RestController
@RequestMapping("/v1/study/sessions")
class StudyController {

  private final StudyService study;
  private final CurrentUser currentUser;

  StudyController(StudyService study, CurrentUser currentUser) {
    this.study = study;
    this.currentUser = currentUser;
  }

  // 201 for a new session; 200 when today's unfinished review is returned instead.
  @PostMapping
  @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  ResponseEntity<SessionView> startSession(@RequestBody StartSession input) {
    StudyService.Started started = study.start(currentUser.id(), input);
    return ResponseEntity.status(started.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(started.session());
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
