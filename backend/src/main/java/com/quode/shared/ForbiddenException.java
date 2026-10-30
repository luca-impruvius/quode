package com.quode.shared;

/** The caller is known but may not do this. Becomes a 403 Problem Details response. */
public class ForbiddenException extends RuntimeException {

  public ForbiddenException() {
    super("Access denied");
  }
}
