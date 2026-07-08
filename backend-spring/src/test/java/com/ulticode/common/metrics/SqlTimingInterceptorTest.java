package com.ulticode.common.metrics;

import com.ulticode.common.time.FakeTimeSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SqlTimingInterceptor}.
 */
class SqlTimingInterceptorTest {

    private static final long SLOW_THRESHOLD_NANOS = 50L * 1_000_000L;
    private static final long FAST_NANOS = 1L * 1_000_000L;
    private static final long SLOW_NANOS = 80L * 1_000_000L;

    private MetricsCollector metricsCollector;
    private FakeTimeSource fakeTime;
    private SqlTimingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollector();
        fakeTime = new FakeTimeSource();
        interceptor = new SqlTimingInterceptor(metricsCollector, fakeTime);
        // Inject the @Value field; tests run without Spring context.
        ReflectionTestUtils.setField(interceptor, "slowQueryMs", 50L);
    }

    @Test
    @DisplayName("fast query (under threshold) increments query count only")
    void fastQueryIncrementsQueryCount() throws Throwable {
        Invocation invocation = mock(Invocation.class);
        fakeTime.pinMonotonic(0L);
        doAnswer(inv -> {
            fakeTime.advanceNanos(FAST_NANOS);
            return "ok";
        }).when(invocation).proceed();

        Object result = interceptor.intercept(invocation);

        assertEquals("ok", result);
        assertEquals(1L, metricsCollector.getQueryCount());
        assertEquals(0L, metricsCollector.getSlowQueryCount());
    }

    @Test
    @DisplayName("slow query (over threshold) increments both counters")
    void slowQueryIncrementsBothCounters() throws Throwable {
        Invocation invocation = mock(Invocation.class);
        fakeTime.pinMonotonic(0L);
        doAnswer(inv -> {
            fakeTime.advanceNanos(SLOW_NANOS);
            return "ok";
        }).when(invocation).proceed();

        interceptor.intercept(invocation);

        assertEquals(1L, metricsCollector.getQueryCount());
        assertEquals(1L, metricsCollector.getSlowQueryCount());
    }

    @Test
    @DisplayName("failed query still increments query count (caller is informed via exception)")
    void failedQueryStillIncrementsQueryCount() throws Throwable {
        Invocation invocation = mock(Invocation.class);
        fakeTime.pinMonotonic(0L);
        doAnswer(inv -> {
            fakeTime.advanceNanos(FAST_NANOS);
            throw new RuntimeException("SQL boom");
        }).when(invocation).proceed();

        assertThrows(RuntimeException.class, () -> {
            try {
                interceptor.intercept(invocation);
            } catch (Throwable t) {
                if (t instanceof RuntimeException) throw (RuntimeException) t;
                throw new RuntimeException(t);
            }
        });
        // queryCount is incremented even on failure (the SQL was executed, just threw)
        assertEquals(1L, metricsCollector.getQueryCount());
        // Slow-query counter is NOT incremented because the call aborted quickly
        assertEquals(0L, metricsCollector.getSlowQueryCount());
    }

    @Test
    @DisplayName("multiple intercept calls accumulate correctly")
    void multipleInterceptsAccumulate() throws Throwable {
        fakeTime.pinMonotonic(0L);
        for (int i = 0; i < 5; i++) {
            final int captured = i;
            Invocation inv = mock(Invocation.class);
            doAnswer(inv2 -> captured).when(inv).proceed();
            interceptor.intercept(inv);
        }
        assertEquals(5L, metricsCollector.getQueryCount());
        assertEquals(0L, metricsCollector.getSlowQueryCount());
    }

    @Test
    @DisplayName("plugin() wraps the target so calls are intercepted")
    void pluginWrapsTarget() throws Throwable {
        // Arrange: mock Executor with a query signature that matches one of
        // our @Signature declarations (update(MappedStatement, Object))
        Executor executor = mock(Executor.class);
        org.apache.ibatis.mapping.MappedStatement ms = mock(org.apache.ibatis.mapping.MappedStatement.class);
        doAnswer(inv -> null).when(executor).update(ms, new Object());

        // Act
        Object wrapped = interceptor.plugin(executor);
        // Plugin.wrap returns a JDK proxy implementing the same interfaces
        // as the target. The proxy IS NOT the same instance as the raw mock.
        assertTrue(wrapped != null, "plugin() must return a non-null target");
        assertTrue(wrapped != executor, "plugin() must return a proxy, not the raw target");

        // Drive the proxied Executor to verify intercept() really fires.
        // We invoke update() through the wrapped object; Plugin's InvocationHandler
        // routes it through our interceptor.intercept(), which should
        // increment queryCount.
        long before = metricsCollector.getQueryCount();
        ((Executor) wrapped).update(ms, new Object());
        long after = metricsCollector.getQueryCount();
        assertEquals(1L, after - before, "wrapped Executor.update() must trigger interceptor");
    }
}
