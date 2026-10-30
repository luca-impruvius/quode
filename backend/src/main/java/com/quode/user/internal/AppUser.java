package com.quode.user.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.CurrentTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "app_user")
public class AppUser {

  // Time-ordered UUIDv7, generated here; the column's uuidv7() default covers plain SQL inserts.
  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  // Always lowercase, so the unique index on lower(email) and the stored value agree.
  @Column(nullable = false, length = 320)
  private String email;

  @Column(name = "google_sub", nullable = false, unique = true)
  private String googleSub;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  // Left out of the INSERT so the column default applies, then read back.
  @Column(name = "time_zone", nullable = false, length = 64)
  @Generated
  private String timeZone;

  // Timestamps come from the database clock (now()), the same as for plain SQL inserts.
  @CurrentTimestamp(event = EventType.INSERT, source = SourceType.DB)
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @CurrentTimestamp(source = SourceType.DB)
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AppUser() {
    // For JPA.
  }

  public AppUser(String email, String googleSub, String displayName) {
    this.email = normalizeEmail(email);
    this.googleSub = googleSub;
    this.displayName = displayName;
  }

  // Trimmed and lowercased with Locale.ROOT; lookups normalize the same way.
  static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getGoogleSub() {
    return googleSub;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
