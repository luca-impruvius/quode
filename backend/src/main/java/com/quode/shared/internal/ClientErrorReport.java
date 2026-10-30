package com.quode.shared.internal;

/** A frontend error as the SPA reports it. Every field is cut to a fixed length before logging. */
record ClientErrorReport(String message, String stack, String url) {

  static final int MAX_MESSAGE = 1000;
  static final int MAX_STACK = 4000;
  static final int MAX_URL = 500;
  static final int MAX_USER_AGENT = 300;

  ClientErrorReport truncated() {
    return new ClientErrorReport(
        cut(message, MAX_MESSAGE), cut(stack, MAX_STACK), cut(url, MAX_URL));
  }

  static String cut(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
