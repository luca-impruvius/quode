package com.quode.user;

import java.time.ZoneId;
import java.util.UUID;

/**
 * A user's daily review limits and time zone, for the review queue (ADR-0011). The user module owns
 * these columns of app_user; other modules read them only through this API.
 */
public interface StudyLimits {

  /** The user's limits. Throws a 404 for an unknown user. */
  Limits of(UUID userId);

  record Limits(int newPerDay, int reviewsPerDay, ZoneId timeZone) {}
}
