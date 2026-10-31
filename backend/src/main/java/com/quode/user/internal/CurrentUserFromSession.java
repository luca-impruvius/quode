package com.quode.user.internal;

import com.quode.user.CurrentUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** The app_user behind the session's Google account, found by its stable {@code sub}. */
@Component
class CurrentUserFromSession implements CurrentUser {

  private final AppUserRepository users;

  CurrentUserFromSession(AppUserRepository users) {
    this.users = users;
  }

  @Override
  public UUID id() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof OidcUser user) {
      return users
          .findByGoogleSub(user.getSubject())
          .map(AppUser::getId)
          .orElseThrow(CurrentUserFromSession::signedOut);
    }
    throw signedOut();
  }

  private static ResponseStatusException signedOut() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  }
}
