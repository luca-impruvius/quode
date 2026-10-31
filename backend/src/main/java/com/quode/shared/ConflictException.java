package com.quode.shared;

/**
 * The request breaks a rule given the current state (a duplicate name, a cycle, something still in
 * use). Becomes a 409 Problem Details response; the message is shown to the client, so keep it a
 * fixed, user-facing sentence.
 */
public class ConflictException extends RuntimeException {

  public ConflictException(String clientMessage) {
    super(clientMessage);
  }
}
