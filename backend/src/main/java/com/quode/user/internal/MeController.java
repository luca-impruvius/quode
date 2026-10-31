package com.quode.user.internal;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class MeController {

  record MeResponse(String name, String email, String timeZone) {}

  private final AppUserRepository users;

  MeController(AppUserRepository users) {
    this.users = users;
  }

  // Read from the database, not the session, so the values are always current.
  @GetMapping("/me")
  MeResponse me(@AuthenticationPrincipal OidcUser principal) {
    AppUser user =
        users
            .findByGoogleSub(principal.getSubject())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    return new MeResponse(user.getDisplayName(), user.getEmail(), user.getTimeZone());
  }
}
