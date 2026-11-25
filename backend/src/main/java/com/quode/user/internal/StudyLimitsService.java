package com.quode.user.internal;

import com.quode.shared.NotFoundException;
import com.quode.user.StudyLimits;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and saves the daily limits (V7). Plain SQL: the AppUser entity doesn't map them. */
@Service
class StudyLimitsService implements StudyLimits {

  static final int MAX_NEW = 100;
  static final int MIN_REVIEWS = 1;
  static final int MAX_REVIEWS = 500;

  private final JdbcTemplate jdbc;

  StudyLimitsService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(readOnly = true)
  public Limits of(UUID userId) {
    List<Limits> found =
        jdbc.query(
            "select new_per_day, reviews_per_day, time_zone from app_user where id = ?",
            (rs, n) ->
                new Limits(
                    rs.getInt("new_per_day"),
                    rs.getInt("reviews_per_day"),
                    ZoneId.of(rs.getString("time_zone"))),
            userId);
    if (found.isEmpty()) {
      throw new NotFoundException("User not found.");
    }
    return found.getFirst();
  }

  static boolean inBounds(Integer newPerDay, Integer reviewsPerDay) {
    return newPerDay != null
        && reviewsPerDay != null
        && newPerDay >= 0
        && newPerDay <= MAX_NEW
        && reviewsPerDay >= MIN_REVIEWS
        && reviewsPerDay <= MAX_REVIEWS;
  }

  @Transactional
  void save(UUID userId, int newPerDay, int reviewsPerDay) {
    jdbc.update(
        "update app_user set new_per_day = ?, reviews_per_day = ?, updated_at = now() where id = ?",
        newPerDay,
        reviewsPerDay,
        userId);
  }
}
