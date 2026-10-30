package com.quode.shared;

/**
 * The requested thing doesn't exist, or the caller may not know it exists. Becomes a 404 Problem
 * Details response; the message is shown to the client, so keep it free of internals.
 */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String clientMessage) {
    super(clientMessage);
  }
}
