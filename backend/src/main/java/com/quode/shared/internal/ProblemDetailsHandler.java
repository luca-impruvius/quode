package com.quode.shared.internal;

import com.quode.shared.ForbiddenException;
import com.quode.shared.NotFoundException;
import io.micrometer.tracing.Tracer;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into Problem Details (RFC 9457) with the request's trace ID. Spring's base
 * class covers framework errors (unknown path, malformed body, wrong method, ...); this class adds
 * validation details, the shared exceptions and a catch-all that never leaks internals.
 */
@RestControllerAdvice
class ProblemDetailsHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);

  private final Tracer tracer;

  ProblemDetailsHandler(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Invalid request");
    List<Map<String, String>> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    Map.of(
                        "field",
                        error.getField(),
                        "message",
                        String.valueOf(error.getDefaultMessage())))
            .toList();
    problem.setProperty("errors", errors);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  @ExceptionHandler
  ResponseEntity<Object> handleNotFound(NotFoundException ex, WebRequest request) {
    return problem(ex, HttpStatus.NOT_FOUND, ex.getMessage(), request);
  }

  @ExceptionHandler
  ResponseEntity<Object> handleForbidden(ForbiddenException ex, WebRequest request) {
    return problem(ex, HttpStatus.FORBIDDEN, ex.getMessage(), request);
  }

  @ExceptionHandler
  ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
    // The full exception goes to the log only; the trace ID ties the response to it.
    log.error("Unexpected error", ex);
    return problem(
        ex,
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Something went wrong. Quote the reference if you report it.",
        request);
  }

  // Every Problem Details response from this handler, including the base class's, passes here.
  @Override
  protected ResponseEntity<Object> createResponseEntity(
      Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (body instanceof ProblemDetail problem) {
      problem.setProperty("traceId", TraceIdFilter.currentTraceId(tracer));
    }
    return super.createResponseEntity(body, headers, status, request);
  }

  private ResponseEntity<Object> problem(
      Exception ex, HttpStatus status, String detail, WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
  }
}
