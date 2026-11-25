package com.quode.review.internal;

/** A question's scheduling numbers; the ease has two decimals. */
record Schedule(int intervalDays, double ease, int reps, int lapses) {

  /** A question never reviewed. */
  static final Schedule NEW = new Schedule(0, 2.5, 0, 0);
}
