package com.quode.study;

import java.util.UUID;

/**
 * Published after an answer is saved. The review module will listen (scheduling is not called from
 * study, CLAUDE.md); quick quizzes don't enrol questions into review (ADR-0012).
 */
public record AttemptRecorded(
    UUID ownerId, UUID sessionId, String sessionKind, UUID questionId, String outcome) {}
