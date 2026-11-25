package com.quode.study;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after an answer is saved, in the same transaction. The review module listens and
 * schedules (never called from study, CLAUDE.md); quizzes don't enrol questions (ADR-0012). {@code
 * answeredAt} is the stored attempt's time, so "today" is the day of the answer, not of handling.
 */
public record AttemptRecorded(
    UUID ownerId,
    UUID sessionId,
    String sessionKind,
    UUID questionId,
    String outcome,
    Instant answeredAt) {}
