package com.ulticode.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * P1-OBS-001: Dubbo-side trace context propagation.
 *
 * <p>The Dubbo filter is the second half of the end-to-end contract.
 * The Dubbo RPC framework does NOT do W3C traceparent propagation
 * out of the box for hand-written filters; we therefore attach the
 * active Micrometer trace id to the {@code X-Ulticode-Trace-Id}
 * invocation attachment so the provider-side filter can recover it.
 *
 * <p>The Dubbo consumer-side and provider-side paths share one
 * {@link DubboTraceFilter} (loaded by SPI via the
 * {@code @Activate(group = {"provider","consumer"})} annotation),
 * so the same bean is invoked on both sides. The side is detected
 * from {@code RpcContext.getServiceContext().isConsumerSide()}.
 */
@DisplayName("P1-OBS-001: DubboTraceFilter")
class DubboTraceFilterTest {

    private DubboTraceFilter filter;

    @BeforeEach
    void setUp() {
        Tracer tracer = mock(Tracer.class);
        TraceContext ctx = mock(TraceContext.class);
        Span span = mock(Span.class);
        when(ctx.traceId()).thenReturn("abcdef00000000000000000000000001");
        when(ctx.spanId()).thenReturn("0000000000000002");
        when(span.context()).thenReturn(ctx);
        when(tracer.currentSpan()).thenReturn(span);

        filter = new DubboTraceFilter(tracer);
    }

    @AfterEach
    void tearDown() {
        RpcContext.removeServiceContext();
        RpcContext.removeServerContext();
    }

    @Test
    @DisplayName("consumer side attaches the active trace id to the invocation")
    void consumerAttachesTraceId() throws Exception {
        // Simulate a consumer-side call: RpcContext is set to consumerSide.
        RpcContext.getServiceContext().setUrl(URL.valueOf("test://localhost/HealthCheckService").addParameter(org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY, org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE));
        Invoker<Object> invoker = mock(Invoker.class);
        Invocation invocation = mock(Invocation.class);
        when(invocation.getServiceName()).thenReturn("com.ulticode.dubbo.provider.HealthCheckService");
        when(invocation.getMethodName()).thenReturn("ping");
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);

        filter.invoke(invoker, invocation);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(invocation, org.mockito.Mockito.atLeastOnce())
                .setAttachment(key.capture(), value.capture());
        // The X-Ulticode-Trace-Id attachment must carry the active
        // trace id so the provider-side filter (or a smoke test that
        // inspects the attachment) can recover the trace.
        assertThat(key.getAllValues()).contains(DubboTraceFilter.TRACE_ID_ATTACHMENT_KEY);
        int idx = key.getAllValues().indexOf(DubboTraceFilter.TRACE_ID_ATTACHMENT_KEY);
        assertThat(value.getAllValues().get(idx))
                .isEqualTo("abcdef00000000000000000000000001");
    }

    @Test
    @DisplayName("consumer side renders a W3C traceparent header from the active span")
    void consumerRendersW3CTraceparent() throws Exception {
        RpcContext.getServiceContext().setUrl(URL.valueOf("test://localhost/HealthCheckService").addParameter(org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY, org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE));
        Invoker<Object> invoker = mock(Invoker.class);
        Invocation invocation = mock(Invocation.class);
        when(invocation.getServiceName()).thenReturn("com.ulticode.dubbo.provider.HealthCheckService");
        when(invocation.getMethodName()).thenReturn("ping");
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);

        filter.invoke(invoker, invocation);

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(invocation, org.mockito.Mockito.atLeastOnce())
                .setAttachment(org.mockito.ArgumentMatchers.eq(DubboTraceFilter.W3C_TRACEPARENT_HEADER), value.capture());
        // W3C traceparent: 00-<32-hex-trace-id>-<16-hex-span-id>-01
        assertThat(value.getValue()).matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$");
    }

    @Test
    @DisplayName("provider side returns the result without re-throwing")
    void providerDoesNotInterfere() throws Exception {
        RpcContext.getServiceContext().setUrl(URL.valueOf("test://localhost/HealthCheckService").addParameter(org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY, org.apache.dubbo.common.constants.CommonConstants.PROVIDER_SIDE));
        Invoker<Object> invoker = mock(Invoker.class);
        Invocation invocation = mock(Invocation.class);
        when(invocation.getServiceName()).thenReturn("com.ulticode.dubbo.provider.HealthCheckService");
        when(invocation.getMethodName()).thenReturn("ping");
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);

        Result returned = filter.invoke(invoker, invocation);

        // Provider side does NOT set attachments (the framework's own
        // observation filter does that). We just verify the chain
        // forwards and the result is intact.
        org.mockito.Mockito.verify(invocation, org.mockito.Mockito.never())
                .setAttachment(org.mockito.ArgumentMatchers.eq(DubboTraceFilter.TRACE_ID_ATTACHMENT_KEY),
                        org.mockito.ArgumentMatchers.anyString());
        assertThat(returned).isSameAs(result);
    }
}
