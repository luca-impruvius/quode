package com.quode.question;

import java.util.UUID;

/**
 * Published, in the creating transaction, for every new question: from the editor and from import
 * (drafts included). The review module enrols it (docs/specs/M3-daily-review.md, Enrolment).
 */
public record QuestionCreated(UUID ownerId, UUID questionId) {}
