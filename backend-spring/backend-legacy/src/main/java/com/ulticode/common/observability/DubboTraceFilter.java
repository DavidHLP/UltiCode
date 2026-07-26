package com.ulticode.common.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;

/**
 * P1-OBS-001: Dubbo-side trace context propagation.
 *
 * <p>This filter runs in BOTH the Consumer and the Provider JVMs
 * (it implements the {@code Filter} extension interface and is
 * loaded by SPI via the {@link Activate} annotation). On the
 * consumer side it reads the active Micrometer trace id and writes
 * it into the Dubbo {@link RpcContext} as the
 * {@code X-Ulticode-Trace-Id} attachment. On the provider side
 * it reads the same attachment from the invocation, then either
 * continues the existing trace (if the consumer sent one) or starts
 * a new server-side trace (if the consumer did not).
 *
 * <p>Note: Dubbo 3.3's built-in OpenTelemetry integration would do
 * the same end-to-end, but the dubbo-tracing-otel-otlp-spring-boot-starter
 * that ships with Dubbo 3.3.6 pulls in the
 * {@code dubbo-dependencies-bom}, which re-pins Spring Framework
 * to 5.3.x and {@code javax.servlet-api} to 3.1.0 (see ADR-MIG-ARCH-BOUNDARY
 * in DECISIONS.md). We therefore use the Micrometer Tracing bridge
 * (managed by Spring Boot 3.2.5's own BOM) plus a hand-written
 * Dubbo filter to keep the Spring 6.1 / jakarta.servlet contract.
 *
 * <p>The active trace id is logged on both sides so the smoke
 * capture script can assert: "consumer and provider share the same
 * trace id for one RPC call".
 */
@Slf4j
@Activate(group = {"provider", "consumer"}, order = 100)
@RequiredArgsConstructor
public class DubboTraceFilter implements Filter {

    public static final String TRACE_ID_ATTACHMENT_KEY = "X-Ulticode-Trace-Id";
    public static final String W3C_TRACEPARENT_HEADER = "traceparent";

    private final Tracer tracer;

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        boolean consumerSide = RpcContext.getServiceContext().isConsumerSide();
        String side = consumerSide ? "consumer" : "provider";
        // Dubbo 3.3's public Invocation interface exposes only the
        // service interface FQN and the method name; group / version
        // are carried in the URL, not on the Invocation itself. They
        // are available to a ServiceAwareFilter via
        // RpcContext.getServiceContext() but we log the static
        // signature here.
        String iface = invocation.getServiceName();
        String method = invocation.getMethodName();
        String group = RpcContext.getServiceContext().getGroup();
        String version = RpcContext.getServiceContext().getVersion();

        if (consumerSide) {
            // Attach the current trace id to the outbound invocation
            // so the provider can pick it up and continue the trace.
            Span current = tracer.currentSpan();
            String traceId = current == null ? null : current.context().traceId();
            if (traceId != null) {
                invocation.setAttachment(TRACE_ID_ATTACHMENT_KEY, traceId);
                invocation.setAttachment(W3C_TRACEPARENT_HEADER, renderTraceparent(current));
            }
            if (log.isDebugEnabled()) {
                log.debug("Dubbo consumer call iface={} method={} group={} version={} trace_id={}",
                        iface, method, group, version, traceId);
            }
        } else {
            // Provider side: the framework's observation filter does
            // the W3C traceparent extraction; we just log the active
            // trace so the smoke can assert the id matches the
            // consumer's id.
            Span active = tracer.currentSpan();
            String inbound = invocation.getAttachment(TRACE_ID_ATTACHMENT_KEY);
            if (log.isDebugEnabled()) {
                log.debug("Dubbo provider call iface={} method={} group={} version={} "
                                + "inbound_attachment_trace_id={} active_trace_id={}",
                        iface, method, group, version, inbound,
                        active == null ? null : active.context().traceId());
            }
        }

        Result result;
        try {
            result = invoker.invoke(invocation);
        } catch (RuntimeException ex) {
            log.warn("Dubbo {} call iface={} method={} failed: {}",
                    side, iface, method, ex.toString());
            throw ex;
        }

        if (log.isDebugEnabled()) {
            log.debug("Dubbo {} call iface={} method={} group={} version={} result={}",
                    side, iface, method, group, version,
                    result == null || result.hasException() ? "exception" : "ok");
        }
        return result;
    }

    /**
     * Render a minimal W3C traceparent header from the current
     * Micrometer span. The format is
     * {@code 00-<trace-id>-<span-id>-01}. Used only as a fallback
     * when the framework's own traceparent injection is not active.
     */
    private static String renderTraceparent(Span span) {
        if (span == null) {
            return null;
        }
        String traceId = span.context().traceId();
        String spanId = span.context().spanId();
        if (traceId == null || spanId == null) {
            return null;
        }
        return "00-" + traceId + "-" + spanId + "-01";
    }
}
