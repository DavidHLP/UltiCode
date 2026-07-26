package com.ulticode.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * P1-OBS-001: HTTP-side W3C traceparent acceptance.
 *
 * <p>The first acceptance criterion says: "HTTP filter extracts/creates
 * W3C traceparent headers". This test asserts the contract we own:
 * after the request is processed, the active trace id is copied to
 * the {@code X-Ulticode-Trace-Id} response header so the smoke and
 * the curl-based integration tests can assert end-to-end
 * propagation without a tracing collector.
 */
@DisplayName("P1-OBS-001: HttpTraceparentFilter")
class HttpTraceparentFilterTest {

    @Test
    @DisplayName("active trace id is copied to X-Ulticode-Trace-Id response header")
    void copiesTraceIdToResponseHeader() throws Exception {
        Tracer tracer = mock(Tracer.class);
        TraceContext ctx = mock(TraceContext.class);
        Span span = mock(Span.class);
        when(ctx.traceId()).thenReturn("000000000000000000000000000000ab");
        when(span.context()).thenReturn(ctx);
        when(tracer.currentSpan()).thenReturn(span);

        HttpTraceparentFilter filter =
                new HttpTraceparentFilter(new ObjectProvider<Tracer>() {
                    @Override public Tracer getObject() { return tracer; }
                    @Override public Tracer getObject(Object... args) { return tracer; }
                    @Override public Tracer getIfAvailable() { return tracer; }
                    @Override public Tracer getIfUnique() { return tracer; }
                    @Override public java.util.stream.Stream<Tracer> stream() { return java.util.stream.Stream.of(tracer); }
                    @Override public void ifAvailable(java.util.function.Consumer<Tracer> c) { c.accept(tracer); }
                    @Override public boolean equals(Object o) { return this == o; }
                    @Override public int hashCode() { return System.identityHashCode(this); }
                });

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/whoami");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(HttpTraceparentFilter.TRACE_ID_HEADER))
                .isEqualTo("000000000000000000000000000000ab");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("missing active span does not throw and does not write a header")
    void missingActiveSpan() throws Exception {
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);

        HttpTraceparentFilter filter =
                new HttpTraceparentFilter(new ObjectProvider<Tracer>() {
                    @Override public Tracer getObject() { return tracer; }
                    @Override public Tracer getObject(Object... args) { return tracer; }
                    @Override public Tracer getIfAvailable() { return tracer; }
                    @Override public Tracer getIfUnique() { return tracer; }
                    @Override public java.util.stream.Stream<Tracer> stream() { return java.util.stream.Stream.of(tracer); }
                    @Override public void ifAvailable(java.util.function.Consumer<Tracer> c) { c.accept(tracer); }
                    @Override public boolean equals(Object o) { return this == o; }
                    @Override public int hashCode() { return System.identityHashCode(this); }
                });

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(HttpTraceparentFilter.TRACE_ID_HEADER)).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
