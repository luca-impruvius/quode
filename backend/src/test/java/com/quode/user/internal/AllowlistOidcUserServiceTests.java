package com.quode.user.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quode.TestcontainersConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.annotation.Transactional;

// Google's answer is stubbed; the allowlist check and the app_user row run for real.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AllowlistOidcUserServiceTests {

  @Autowired private AppUserRepository repository;

  @Autowired private SecurityProperties properties;

  @Autowired private ClientRegistrationRepository registrations;

  @Test
  void firstAllowedLoginCreatesTheUser() {
    OidcUser user = service(googleUser("sub-1", "Allowed@Example.com", true)).loadUser(request());

    assertThat(user.getSubject()).isEqualTo("sub-1");
    AppUser saved = repository.findByGoogleSub("sub-1").orElseThrow();
    assertThat(saved.getEmail()).isEqualTo("allowed@example.com");
    assertThat(saved.getDisplayName()).isEqualTo("Test User");
    assertThat(saved.getTimeZone()).isEqualTo("Europe/Bucharest");
  }

  @Test
  void laterLoginsFindTheUserByGoogleSub() {
    service(googleUser("sub-1", "allowed@example.com", true)).loadUser(request());
    service(googleUser("sub-1", "allowed@example.com", true)).loadUser(request());

    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  void blocksAnEmailNotOnTheList() {
    assertDenied(service(googleUser("sub-2", "someone@example.com", true)));
  }

  @Test
  void blocksAnUnverifiedEmail() {
    assertDenied(service(googleUser("sub-3", "allowed@example.com", false)));
  }

  @Test
  void anEmptyAllowlistBlocksEveryone() {
    AllowlistOidcUserService service =
        new AllowlistOidcUserService(
            request -> googleUser("sub-4", "allowed@example.com", true),
            new SecurityProperties(List.of()),
            repository);

    assertDenied(service);
  }

  private void assertDenied(AllowlistOidcUserService service) {
    assertThatThrownBy(() -> service.loadUser(request()))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
        .isEqualTo("access_denied");
    assertThat(repository.count()).isZero();
  }

  private AllowlistOidcUserService service(OidcUser fromGoogle) {
    return new AllowlistOidcUserService(request -> fromGoogle, properties, repository);
  }

  private static OidcUser googleUser(String sub, String email, boolean verified) {
    OidcIdToken token =
        new OidcIdToken(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("sub", sub, "email", email, "email_verified", verified, "name", "Test User"));
    return new DefaultOidcUser(List.of(), token);
  }

  private OidcUserRequest request() {
    ClientRegistration google = registrations.findByRegistrationId("google");
    OAuth2AccessToken accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access",
            Instant.now(),
            Instant.now().plusSeconds(60));
    OidcIdToken idToken =
        new OidcIdToken(
            "token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "ignored"));
    return new OidcUserRequest(google, accessToken, idToken);
  }
}
