package com.quode.shared.internal;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the request's trace ID in the {@value #HEADER} response header, so the UI can show it as an
 * error reference that matches the log lines (ADR-0027). The ID is also kept as a request
 * attribute, because error pages render after the request's span has ended.
 */
// Runs just inside Spring's ServerHttpObservationFilter (HIGHEST_PRECEDENCE + 1), which starts the
// request's span, and before anything can commit the response.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class TraceIdFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Trace-Id";

  static final String ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";

  private final Tracer tracer;

  TraceIdFilter(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String traceId = currentTraceId(tracer);
    if (traceId != null) {
      request.setAttribute(ATTRIBUTE, traceId);
      response.setHeader(HEADER, traceId);
    }
    chain.doFilter(request, response);
  }

  static String currentTraceId(Tracer tracer) {
    Span span = tracer.currentSpan();
    return span == null ? null : span.context().traceId();
  }
}
