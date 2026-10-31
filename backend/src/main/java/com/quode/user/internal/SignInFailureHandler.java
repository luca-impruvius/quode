package com.quode.user.internal;

import com.quode.shared.ErrorDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * Answers a failed Google sign-in with Problem Details instead of Spring's /login?error redirect:
 * 403 for an account that isn't allowed, 401 for anything else (expired or tampered callback).
 */
class SignInFailureHandler implements AuthenticationFailureHandler {

  static final String NOT_ALLOWED = "This Google account isn't allowed to use Quode.";
  static final String FAILED = "Sign-in failed. Please try again.";

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    boolean notAllowed =
        exception instanceof OAuth2AuthenticationException oauth
            && OAuth2ErrorCodes.ACCESS_DENIED.equals(oauth.getError().getErrorCode());
    request.setAttribute(ErrorDetails.ATTRIBUTE, notAllowed ? NOT_ALLOWED : FAILED);
    response.sendError(
        notAllowed ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED);
  }
}
