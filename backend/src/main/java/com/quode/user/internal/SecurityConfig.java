package com.quode.user.internal;

import com.quode.shared.ErrorDetails;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Google sign-in with an email allowlist (ADR-0015), sessions in Postgres (ADR-0017). Paths here
 * are relative to the /api context path. The API answers 401 Problem Details, never redirects; only
 * the browser-facing sign-in redirects (to Google, then back to / or /login?error=...).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

  static final String SIGN_IN_REQUIRED = "Sign in to continue.";

  // Actuator lives on the management port, reachable only from localhost or an SSH tunnel.
  @Bean
  @Order(1)
  SecurityFilterChain managementSecurity(HttpSecurity http) throws Exception {
    http.securityMatcher(EndpointRequest.toAnyEndpoint())
        .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable());
    return http.build();
  }

  // Loads the Google profile (ID token plus userinfo); a bean so tests can avoid the network call.
  @Bean
  OidcUserService googleUserService() {
    return new OidcUserService();
  }

  @Bean
  @Order(2)
  SecurityFilterChain appSecurity(
      HttpSecurity http,
      SecurityProperties properties,
      AppUserRepository users,
      OidcUserService google,
      @Value("${springdoc.api-docs.enabled:true}") boolean apiDocs)
      throws Exception {
    PathPatternRequestMatcher clientErrors =
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/client-errors");

    http.authorizeHttpRequests(
            requests -> {
              // Error responses (rendered by the error controller) and the public endpoints.
              requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
              requests.requestMatchers("/health", "/error").permitAll();
              requests.requestMatchers(clientErrors).permitAll();
              // The OpenAPI spec only exists where springdoc is on: never in prod.
              if (apiDocs) {
                requests.requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll();
              }
              requests.anyRequest().authenticated();
            })
        .oauth2Login(
            login ->
                login
                    // Also switches off Spring's generated login page.
                    .loginPage("/oauth2/authorization/google")
                    .userInfoEndpoint(
                        info ->
                            info.oidcUserService(
                                new AllowlistOidcUserService(google, properties, users)))
                    // To the app root, not /api/ (the context path).
                    .successHandler((request, response, auth) -> response.sendRedirect("/"))
                    .failureHandler(new SignInFailureHandler()))
        // No saved request: the success handler always goes to /, and saving one would create a
        // 30-day session for every signed-out request.
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    (request, response, e) -> {
                      request.setAttribute(ErrorDetails.ATTRIBUTE, SIGN_IN_REQUIRED);
                      response.sendError(HttpStatus.UNAUTHORIZED.value());
                    }))
        .csrf(
            csrf -> {
              // Cookie XSRF-TOKEN, sent back by the SPA in the X-XSRF-TOKEN header.
              csrf.spa();
              CookieCsrfTokenRepository tokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
              tokens.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Lax"));
              // Path / (not the /api context path), so the SPA's pages can read it.
              tokens.setCookiePath("/");
              csrf.csrfTokenRepository(tokens);
              // Anonymous, session-less error reports from the browser.
              csrf.ignoringRequestMatchers(clientErrors);
            })
        .logout(
            logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutSuccessHandler(
                        new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
    return http.build();
  }
}
