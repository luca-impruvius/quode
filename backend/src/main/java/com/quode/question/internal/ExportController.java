package com.quode.question.internal;

import com.quode.user.CurrentUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Export in the import format, for filling answers outside the app (M2). */
@RestController
@RequestMapping("/v1/questions/export")
class ExportController {

  static final String BAD_STATUS = "Export status is DRAFT or ACTIVE.";

  private final ExportService exports;
  private final CurrentUser currentUser;

  ExportController(ExportService exports, CurrentUser currentUser) {
    this.exports = exports;
    this.currentUser = currentUser;
  }

  /** The non-archived questions of {@code status} (default DRAFT), optionally under a topic. */
  @GetMapping
  ExportFile export(
      @RequestParam(defaultValue = "DRAFT") String status,
      @RequestParam(required = false) UUID topicId) {
    QuestionStatus value = null;
    for (QuestionStatus s : QuestionStatus.values()) {
      if (s.name().equalsIgnoreCase(status)) {
        value = s;
      }
    }
    if (value == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, BAD_STATUS);
    }
    return exports.export(currentUser.id(), value, topicId);
  }
}
