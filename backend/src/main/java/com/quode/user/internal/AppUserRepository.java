package com.quode.user.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  // Normalizes the argument exactly like AppUser stores it, so " Me@Example.com" finds the user.
  default Optional<AppUser> findByEmail(String email) {
    return findByLowerEmail(AppUser.normalizeEmail(email));
  }

  // Call findByEmail, not this: it skips the trim. lower() on both sides matches the
  // app_user_email_lower_key index. A derived
  // findByEmailIgnoreCase would compare upper() values and skip the index.
  @Query("select u from AppUser u where lower(u.email) = lower(:email)")
  Optional<AppUser> findByLowerEmail(String email);

  // Google's stable account ID; how sign-in finds the user (google_sub is unique).
  Optional<AppUser> findByGoogleSub(String googleSub);
}
