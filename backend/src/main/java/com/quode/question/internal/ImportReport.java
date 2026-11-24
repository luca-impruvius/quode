package com.quode.question.internal;

import java.util.List;

/**
 * What an import did, or would do on a dry run. {@code created}: new questions ready to study;
 * {@code drafts}: new questions still without an answer; {@code filled}: drafts that got their
 * answer by id; {@code problems}: the questions left out, by position in the file (1 = first).
 */
record ImportReport(
    int created,
    int filled,
    int drafts,
    int skippedDuplicates,
    int topicsCreated,
    List<ImportProblem> problems) {

  record ImportProblem(int index, String reason) {}
}
