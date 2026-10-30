package com.quode.shared.internal;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /api/client-errors}: logs frontend errors next to the server logs, with the trace ID
 * (ADR-0027). Open to everyone, so errors on the sign-in screen are logged too; the body is capped
 * at {@value #MAX_BODY_BYTES} bytes and every field is truncated.
 */
@RestController
class ClientErrorController {

  static final int MAX_BODY_BYTES = 8 * 1024;

  private static final Logger log = LoggerFactory.getLogger(ClientErrorController.class);

  private final JsonMapper jsonMapper;

  ClientErrorController(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  // JSON only: browsers send cross-site JSON posts only after a CORS preflight, which fails here,
  // so other websites can't make their visitors write to our logs.
  @PostMapping(value = "/client-errors", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void report(HttpServletRequest request) throws IOException {
    // Read at most one byte past the limit, so an oversized or chunked body is never buffered
    // whole.
    byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
    if (body.length > MAX_BODY_BYTES) {
      throw new ResponseStatusException(
          HttpStatus.CONTENT_TOO_LARGE, "Client error reports are limited to 8 KB");
    }

    ClientErrorReport report;
    try {
      report = jsonMapper.readValue(body, ClientErrorReport.class);
    } catch (JacksonException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed JSON");
    }
    // A body of `null` parses to no report at all.
    if (report == null || report.message() == null || report.message().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
    }
    report = report.truncated();
    String userAgent =
        ClientErrorReport.cut(
            request.getHeader(HttpHeaders.USER_AGENT), ClientErrorReport.MAX_USER_AGENT);

    // URL and message go in the text, readable in every log format. Stack and user agent become
    // fields of the production JSON log, which escapes all values so client text can't forge
    // lines (the local plain-text log does not escape newlines).
    log.atWarn()
        .addKeyValue("client.stack", report.stack())
        .addKeyValue("client.user_agent", userAgent)
        .log("Client error at {}: {}", report.url(), report.message());
  }
}
