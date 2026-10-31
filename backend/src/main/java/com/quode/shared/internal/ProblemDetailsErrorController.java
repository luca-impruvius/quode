package com.quode.shared.internal;

import com.quode.shared.ErrorDetails;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Boot's default error page for errors raised outside Spring MVC (filters, sendError), so
 * those responses are Problem Details with the trace ID too. Never shows the exception or message;
 * the only detail is one our code set through {@link ErrorDetails}.
 */
@RestController
class ProblemDetailsErrorController implements ErrorController {

  @RequestMapping("${server.error.path:/error}")
  ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
    // Called directly (no error dispatch), the path simply doesn't exist.
    HttpStatus status =
        request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
            ? HttpStatus.resolve(code)
            : HttpStatus.NOT_FOUND;
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    ProblemDetail problem = ProblemDetail.forStatus(status);
    // The path the client asked for, not /api/error.
    if (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String uri) {
      problem.setInstance(URI.create(uri));
    }
    // A fixed message chosen by our own code (security handlers), never an exception message.
    if (request.getAttribute(ErrorDetails.ATTRIBUTE) instanceof String detail) {
      problem.setDetail(detail);
    }
    ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
    if (request.getAttribute(TraceIdFilter.ATTRIBUTE) instanceof String traceId) {
      problem.setProperty("traceId", traceId);
      // The container may have reset the response headers before the error dispatch.
      response.header(TraceIdFilter.HEADER, traceId);
    }
    return response.body(problem);
  }
}
