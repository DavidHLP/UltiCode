package com.ulticode.common.metrics;

import com.ulticode.common.time.TimeSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * MyBatis {@link Interceptor} that counts every SQL execution and
 * tracks slow queries for {@code /monitoring/database} visibility.
 *
 * <p>Registered via {@code MybatisPlusConfig#mybatisCustomizer} so
 * it lives outside the MyBatis-Plus inner-interceptor chain (which
 * has no afterQuery hook). Targets the {@code Executor} level so
 * every query and update is timed exactly once.
 *
 * <p>Time source: monotonic nanoseconds come from the {@link TimeSource}
 * seam so tests can pin a deterministic cost instead of
 * {@code Thread.sleep(80)}.
 *
 * @author UltiCode
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class,
                        RowBounds.class, ResultHandler.class}),
        // Streaming cursor query (MyBatis 3.x); rarer path but should still
        // be counted to avoid under-reporting queryCount.
        @Signature(type = Executor.class, method = "queryCursor",
                args = {MappedStatement.class, Object.class, RowBounds.class})
})
public class SqlTimingInterceptor implements Interceptor {

    private static final long NANOS_PER_MS = 1_000_000L;

    private final MetricsCollector metricsCollector;
    private final TimeSource timeSource;

    @Value("${app.monitoring.slow-query-ms:500}")
    private long slowQueryMs;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startNs = timeSource.monotonicNanos();
        try {
            return invocation.proceed();
        } finally {
            long costMs = (timeSource.monotonicNanos() - startNs) / NANOS_PER_MS;
            metricsCollector.incrementQuery();
            if (costMs > slowQueryMs) {
                metricsCollector.incrementSlowQuery();
                if (log.isDebugEnabled()) {
                    log.debug("Slow query detected: {}ms (threshold={}ms)",
                            costMs, slowQueryMs);
                }
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    /**
     * Required by {@link Interceptor} contract but unused. Threshold is
     * injected via {@code @Value("${app.monitoring.slow-query-ms:500}")}
     * on the {@code slowQueryMs} field instead of MyBatis properties.
     *
     * @param properties ignored
     */
    @Override
    public void setProperties(Properties properties) {
        // intentionally empty: thresholds come from @Value, not properties
    }
}
