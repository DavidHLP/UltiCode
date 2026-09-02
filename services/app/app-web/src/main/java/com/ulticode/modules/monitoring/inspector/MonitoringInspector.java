package com.ulticode.modules.monitoring.inspector;

import com.ulticode.app.api.service.QueueHealthProbePort;
import com.ulticode.modules.monitoring.dto.DatabaseStatsVO;
import com.ulticode.modules.monitoring.dto.QueueStatsVO;
import com.ulticode.modules.monitoring.dto.RedisStatsVO;
import com.ulticode.modules.monitoring.dto.ResourceUsageVO;
import com.ulticode.modules.monitoring.dto.SystemHealthVO;
import com.ulticode.modules.monitoring.dto.SystemInfoVO;

import java.util.List;

/**
 * Read-only inspection deep module for system monitoring.
 *
 * <p>Owns every pure-read path that asks the running JVM, the JDBC
 * datasource, the Redis client, and the queue subsystem about their
 * state. Queue depth is sourced through the App API's
 * {@link QueueHealthProbePort} contract (one queue truth); monitoring
 * never reads broker key layouts directly.
 *
 * <p>The interface is intentionally narrow so callers
 * (admin/diagnostic controllers, future Prometheus exporter, ad-hoc
 * Arthas probes) get a stable view of the platform without reaching
 * into the {@code JdbcTemplate}, the {@code RedisTemplate}, or the
 * queue beans themselves.
 *
 * <p>Deliberately side-effect free: every method returns a snapshot
 * and does not mutate application state. The Spring
 * {@link org.springframework.cache.annotation.Cacheable} annotation
 * on the default implementation only caches return-value
 * serialization, never the underlying datasource or Redis rows.
 *
 * <p>Test surface: a unit test for this module mocks the
 * {@code DataSource}, the {@code RedisConnectionFactory}, the
 * {@code RedisTemplate}, {@code MetricsCollector}, and the
 * {@code QueueHealthProbePort} directly; no write-path collaborator
 * is needed because there is no write path.
 *
 * @see com.ulticode.common.metrics.MetricsCollector the matching
 *      writer of query metrics this inspector reads from
 * @see QueueHealthProbePort the queue
 *      health probe contract this inspector delegates queue depth to
 */
public interface MonitoringInspector {

    /**
     * Snapshot of JVM and process metadata: uptime, java version,
     * platform, hostname, env, pid, application version.
     *
     * @return populated system info VO; the inspector never returns
     *         {@code null} but {@code getHostname()} may yield the
     *         literal string {@code "unknown"} when the host name is
     *         not resolvable in restricted environments.
     */
    SystemInfoVO getSystemInfo();

    /**
     * Snapshot of JVM resource pressure: heap/non-heap usage, process
     * and system CPU load, available processors, current thread
     * count.
     *
     * @return populated resource usage VO; CPU readings may be
     *         {@code -1.0} when the underlying MXBean is unavailable
     *         (typical inside some container runtimes).
     */
    ResourceUsageVO getResourceUsage();

    /**
     * Snapshot of MySQL connection-pool pressure plus rolling query
     * counters from {@code MetricsCollector}.
     *
     * @return database stats VO with {@code status} equal to either
     *         {@code "healthy"} or {@code "unhealthy"}; the inspector
     *         swallows the underlying {@link java.sql.SQLException}
     *         so the endpoint stays responsive even when the database
     *         is down.
     */
    DatabaseStatsVO getDatabaseStats();

    /**
     * Snapshot of every known application queue
     * ({@code judge_queue}, {@code notification_queue},
     * {@code email_queue}). Depth is sourced through the App API's
     * {@link QueueHealthProbePort} contract, so it reflects the queue depth
     * the active backend treats as truth.
     *
     * <p>The wire shape (the {@link QueueStatsVO} fields the
     * management frontend reads) is preserved; a probe failure on a
     * single queue surfaces as zeros on that queue's VO and is
     * additionally translated into an unhealthy sub-check by
     * {@link #getHealthCheck()} so the failure is never folded into
     * "queue empty and healthy".
     *
     * @return non-empty list of queue stats VOs in the order the
     *         queue names appear in the inspector's static
     *         configuration.
     */
    List<QueueStatsVO> getQueueStats();

    /**
     * Snapshot of Redis server info: version, used memory,
     * connected clients, total keys, uptime.
     *
     * @return Redis stats VO with {@code connected} flipped to
     *         {@code false} (and zero-valued counters) when the
     *         Redis ping handshake fails.
     */
    RedisStatsVO getRedisStats();

    /**
     * Aggregated system health probe: database ping, Redis ping,
     * queue pressure. Always returns a non-null VO with a
     * timestamp; the overall {@code status} is the worst status
     * across the three sub-checks
     * ({@code "unhealthy"} &gt; {@code "degraded"} &gt; {@code "healthy"}).
     *
     * @return system health VO containing one {@code HealthCheck}
     *         per probed service.
     */
    SystemHealthVO getHealthCheck();
}
