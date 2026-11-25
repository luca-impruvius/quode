package com.quode.question.internal;

import com.quode.user.CurrentUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bulk import of questions (docs/specs/M2-exam-prep-mvp.md). */
@RestController
@RequestMapping("/v1/imports")
class ImportController {

  private final ImportService imports;
  private final CurrentUser currentUser;

  ImportController(ImportService imports, CurrentUser currentUser) {
    this.imports = imports;
    this.currentUser = currentUser;
  }

  /** Imports the questions; with {@code dryRun=true} only reports what an import would do. */
  @PostMapping
  ImportReport importQuestions(
      @RequestParam(defaultValue = "false") boolean dryRun, @RequestBody ImportInput input) {
    return imports.importQuestions(currentUser.id(), input, dryRun);
  }
}
