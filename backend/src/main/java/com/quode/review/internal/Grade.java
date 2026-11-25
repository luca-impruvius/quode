package com.quode.review.internal;

/** The three grades of an answer (docs/spaced-repetition.md). */
enum Grade {
  MISSED,
  PARTIAL,
  GOT_IT;

  /** From an attempt's stored outcome: CORRECT, PARTIAL or WRONG. */
  static Grade ofOutcome(String outcome) {
    return switch (outcome) {
      case "CORRECT" -> GOT_IT;
      case "PARTIAL" -> PARTIAL;
      default -> MISSED;
    };
  }
}
