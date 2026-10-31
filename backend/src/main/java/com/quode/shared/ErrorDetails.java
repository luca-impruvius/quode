package com.quode.shared;

/**
 * Lets code outside Spring MVC (security filters) give an error response a user-facing detail: set
 * {@link #ATTRIBUTE} on the request to a fixed, safe message, then call {@code sendError}. The
 * error controller copies it into the Problem Details. Never put exception messages here.
 */
public final class ErrorDetails {

  public static final String ATTRIBUTE = ErrorDetails.class.getName() + ".detail";

  private ErrorDetails() {}
}
