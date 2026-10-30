package com.quode.shared.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Hides incoming trace-context headers, so every request starts a fresh trace. The backend is the
 * edge (Caddy starts no traces), and a client-chosen trace ID could make its log lines show up
 * under someone else's error reference.
 */
// Must run before ServerHttpObservationFilter (HIGHEST_PRECEDENCE + 1), which reads these headers.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class IncomingTraceContextFilter extends OncePerRequestFilter {

  static final Set<String> HIDDEN = Set.of("traceparent", "tracestate", "b3", "baggage");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    chain.doFilter(new WithoutTraceContext(request), response);
  }

  static final class WithoutTraceContext extends HttpServletRequestWrapper {

    WithoutTraceContext(HttpServletRequest request) {
      super(request);
    }

    private static boolean hidden(String name) {
      return name != null && HIDDEN.contains(name.toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public String getHeader(String name) {
      return hidden(name) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return hidden(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      List<String> names =
          Collections.list(super.getHeaderNames()).stream().filter(n -> !hidden(n)).toList();
      return Collections.enumeration(names);
    }
  }
}
