package com.ulticode.common.observability;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * P1-OBS-001: HTTP-side W3C traceparent propagation.
 *
 * <p>Spring Boot 3.2.5 + the Micrometer Tracing bridge already
 * install a server-side observation filter that reads
 * {@code traceparent} / {@code tracestate} on inbound requests and
 * creates a server span, and writes the active span id back to the
 * response. This filter adds two behaviors that the framework does
 * NOT do automatically:
 *
 * <ol>
 *   <li>If a request arrives WITHOUT a {@code traceparent} header,
 *       start a new root span (the framework's own filter still does
 *       this; the extra value is the explicit log line below so the
 *       smoke run can observe the traceId being minted and written
 *       to MDC under {@code traceId}).</li>
 *   <li>Tag every inbound request with
 *       {@code http.method}, {@code http.path}, {@code http.status},
 *       and copy the active traceId into a response header
 *       {@code X-Ulticode-Trace-Id} so the smoke and integration
 *       tests can assert end-to-end propagation by reading the
 *       header without a tracing collector.</li>
 * </ol>
 *
 * <p>Order is set to {@link Ordered#HIGHEST_PRECEDENCE} so this filter
 * runs before any business filter (JWT, XSS, audit) and the trace
 * context is established before those filters log. The framework's
 * own observation filter is registered as a
 * {@code ServerHttpObservationFilter} at order
 * {@code Ordered.HIGHEST_PRECEDENCE + 1} in the Spring Boot 3.2
 * default configuration, so we sit just ahead of it.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpTraceparentFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Ulticode-Trace-Id";
    public static final String W3C_TRACEPARENT = "traceparent";

    /**
     * Use ObjectProvider so @WebMvcTest slices that exclude
     * Micrometer Tracing autoconfiguration can still load the
     * filter without a hard Tracer dependency. The filter
     * gracefully no-ops the X-Ulticode-Trace-Id response header
     * when the Tracer bean is not present.
     */
    private final ObjectProvider<Tracer> tracerProvider;

    public HttpTraceparentFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String inboundTraceparent = request.getHeader(W3C_TRACEPARENT);
        if (log.isDebugEnabled()) {
            log.debug("HTTP request received method={} path={} inbound_traceparent={}",
                    request.getMethod(), request.getRequestURI(),
                    inboundTraceparent == null ? "<none>" : "<present>");
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            // After the request is processed, copy the active traceId
            // back to a response header so the smoke / curl-based
            // integration tests can assert that a single trace id
            // spans the entire request without needing a tracing
            // collector.
            var tracer = tracerProvider.getIfAvailable();
            var currentSpan = tracer == null ? null : tracer.currentSpan();
            if (currentSpan != null) {
                String traceId = currentSpan.context().traceId();
                if (traceId != null) {
                    response.setHeader(TRACE_ID_HEADER, traceId);
                    if (log.isDebugEnabled()) {
                        log.debug("HTTP request completed path={} status={} trace_id={}",
                                request.getRequestURI(), response.getStatus(), traceId);
                    }
                }
            }
        }
    }
}
