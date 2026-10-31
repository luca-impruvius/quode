package com.quode.user.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * The Google callback is a browser navigation, so a failed sign-in goes back to the app's login
 * screen with a reason it can show: "no-access" for an account that isn't allowed, "failed" for
 * anything else (cancelled at Google, expired or tampered callback).
 */
class SignInFailureHandler implements AuthenticationFailureHandler {

  static final String NO_ACCESS = "/login?error=no-access";
  static final String FAILED = "/login?error=failed";

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    boolean notAllowed =
        exception instanceof OAuth2AuthenticationException oauth
            && AllowlistOidcUserService.NOT_ALLOWED.equals(oauth.getError().getErrorCode());
    // Paths from the site root, not the /api context path: the login screen is the SPA's.
    response.sendRedirect(notAllowed ? NO_ACCESS : FAILED);
  }
}
