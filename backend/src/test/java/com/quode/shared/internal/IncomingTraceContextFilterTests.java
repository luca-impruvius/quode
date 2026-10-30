package com.quode.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IncomingTraceContextFilterTests {

  @Test
  void hidesTraceContextHeadersAndKeepsTheRest() {
    MockHttpServletRequest original = new MockHttpServletRequest();
    original.addHeader("Traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    original.addHeader("tracestate", "x=1");
    original.addHeader("b3", "80f198ee56343ba864fe8b2a57d3eff7-e457b5a2e4d86bd1-1");
    original.addHeader("baggage", "user=1");
    original.addHeader("Accept", "application/json");

    var request = new IncomingTraceContextFilter.WithoutTraceContext(original);

    assertThat(request.getHeader("traceparent")).isNull();
    assertThat(request.getHeader("TRACEPARENT")).isNull();
    assertThat(request.getHeaders("tracestate").hasMoreElements()).isFalse();
    assertThat(Collections.list(request.getHeaderNames()))
        .extracting(String::toLowerCase)
        .containsExactly("accept");
    assertThat(request.getHeader("Accept")).isEqualTo("application/json");
  }
}
