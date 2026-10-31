package com.quode.user.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Runs after Google has signed the user in: only verified emails on the allowlist get a session.
 * The first allowed login creates the app_user; later logins find it by Google's stable {@code
 * sub}, so an email change at Google doesn't create a second user.
 */
class AllowlistOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

  private static final Logger log = LoggerFactory.getLogger(AllowlistOidcUserService.class);

  private static final int MAX_DISPLAY_NAME = 100;

  private final OAuth2UserService<OidcUserRequest, OidcUser> google;
  private final SecurityProperties properties;
  private final AppUserRepository users;

  AllowlistOidcUserService(
      OAuth2UserService<OidcUserRequest, OidcUser> google,
      SecurityProperties properties,
      AppUserRepository users) {
    this.google = google;
    this.properties = properties;
    this.users = users;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest request) {
    OidcUser user = google.loadUser(request);
    if (!Boolean.TRUE.equals(user.getEmailVerified()) || !properties.allows(user.getEmail())) {
      // No email in the log: it's personal data of someone who isn't a user.
      log.warn("Sign-in refused: Google account not on the allowlist or email not verified");
      throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED));
    }
    if (users.findByGoogleSub(user.getSubject()).isEmpty()) {
      users.save(new AppUser(user.getEmail(), user.getSubject(), displayName(user)));
    }
    return user;
  }

  private static String displayName(OidcUser user) {
    String name = user.getFullName();
    if (name == null || name.isBlank()) {
      name = user.getEmail();
    }
    name = name.strip();
    return name.length() > MAX_DISPLAY_NAME ? name.substring(0, MAX_DISPLAY_NAME) : name;
  }
}
