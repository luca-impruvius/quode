package com.quode.review.internal;

/** Computes a question's next schedule from an answer; SM-2 now, maybe FSRS later (ADR-0001). */
interface SchedulingStrategy {

  Schedule next(Schedule current, Grade grade);
}
