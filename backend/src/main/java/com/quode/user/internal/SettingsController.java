package com.quode.user.internal;

import com.quode.user.CurrentUser;
import com.quode.user.StudyLimits.Limits;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Me → Settings: the daily review limits (docs/specs/M3-daily-review.md). */
@RestController
@RequestMapping("/v1/me/settings")
class SettingsController {

  static final String OUT_OF_BOUNDS = "New per day is 0–100 and reviews per day 1–500.";

  /** The limits to save; both are required. */
  record SettingsInput(Integer newPerDay, Integer reviewsPerDay) {}

  record Settings(int newPerDay, int reviewsPerDay) {}

  private final StudyLimitsService limits;
  private final CurrentUser currentUser;

  SettingsController(StudyLimitsService limits, CurrentUser currentUser) {
    this.limits = limits;
    this.currentUser = currentUser;
  }

  @GetMapping
  Settings getSettings() {
    return view(limits.of(currentUser.id()));
  }

  @PutMapping
  Settings saveSettings(@RequestBody SettingsInput input) {
    if (!StudyLimitsService.inBounds(input.newPerDay(), input.reviewsPerDay())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, OUT_OF_BOUNDS);
    }
    UUID me = currentUser.id();
    limits.save(me, input.newPerDay(), input.reviewsPerDay());
    return view(limits.of(me));
  }

  private static Settings view(Limits limits) {
    return new Settings(limits.newPerDay(), limits.reviewsPerDay());
  }
}
