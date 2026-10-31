package com.quode.user.internal;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** The Google accounts allowed to sign in (ADR-0015). An empty list lets nobody in. */
@ConfigurationProperties("quode.security")
record SecurityProperties(List<String> allowedEmails) {

  SecurityProperties {
    allowedEmails = allowedEmails == null ? List.of() : List.copyOf(allowedEmails);
  }

  boolean allows(String email) {
    return email != null && normalized().contains(AppUser.normalizeEmail(email));
  }

  // Normalized like AppUser stores emails; blank entries (an empty variable) are ignored.
  private Set<String> normalized() {
    return allowedEmails.stream()
        .filter(e -> !e.isBlank())
        .map(AppUser::normalizeEmail)
        .collect(Collectors.toSet());
  }
}
