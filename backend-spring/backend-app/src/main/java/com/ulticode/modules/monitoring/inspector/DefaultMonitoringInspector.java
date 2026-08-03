package com.ulticode.modules.monitoring.inspector;

import com.ulticode.common.metrics.MetricsCollector;
import com.ulticode.common.system.SystemProbe;
import com.ulticode.common.time.TimeSource;
import com.ulticode.modules.monitoring.dto.DatabaseStatsVO;
import com.ulticode.modules.monitoring.dto.QueueStatsVO;
import com.ulticode.modules.monitoring.dto.RedisStatsVO;
import com.ulticode.modules.monitoring.dto.ResourceUsageVO;
import com.ulticode.modules.monitoring.dto.SystemHealthVO;
import com.ulticode.modules.monitoring.dto.SystemInfoVO;

import com.ulticode.app.api.dto.ProbeStatus;
import com.ulticode.app.api.dto.QueueHealthSnapshotDTO;
import com.ulticode.app.api.service.QueueHealthProbePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Default adapter for {@link MonitoringInspector}. Side-effect free:
 * reads from the JVM MXBeans, the {@code DataSource}, the
 * {@code RedisTemplate}, and the queue module's {@link QueueInspector}
 * port only.
 *
 * <p>Owns its own copy of the inspection logic — no inheritance from
 * any prior {@code MonitoringServiceImpl} class — so this read module
 * is independent of any write-path bean graph (none exists for the
 * monitoring subsystem; reads are the entire contract).
 *
 * <p><b>One queue truth</b>: queue depth is delegated to the queue
 * module's {@link QueueInspector#getQueueHealthSnapshot(String)}. An
 * earlier revision of this class probed a BullMQ (Node.js) key layout
 * via {@code SCARD}/{@code LLEN} that no Java writer in this repo
 * ever produced, so every queue always read empty and the health
 * check was permanently green — even during a Redis outage. The
 * current shape closes that loop: the queue inspector returns the
 * real Redisson {@code RQueue.size()} (or XPENDING total for the
 * Stream backend), and any probe failure is carried back as
 * {@link ProbeStatus#PROBE_FAILED} so this inspector can fail closed
 * (surface unhealthy) instead of folding the failure into
 * "queue empty and healthy".
 *
 * <p>Public {@code @Value} fields ({@code applicationName},
 * {@code applicationVersion}, {@code activeProfile}) participate in
 * read operations only; their defaults keep the inspector usable
 * even when {@code application.yml} or Nacos configuration is absent.
 *
 * <p>Cache strategy: methods that touch external systems
 * ({@link #getSystemInfo}, {@link #getResourceUsage},
 * {@link #getDatabaseStats}, {@link #getQueueStats},
 * {@link #getRedisStats}) are wrapped in
 * {@link Cacheable @Cacheable(cacheNames = "monitoring", key = ...)}
 * so the admin dashboard does not hammer the datasource or Redis
 * between manual reloads. The health probe
 * {@link #getHealthCheck} is intentionally not cached — its purpose
 * is to surface live failure.
 */
@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class DefaultMonitoringInspector implements MonitoringInspector {

    /**
     * Static configuration: the application queues the queue module
     * owns. Order is part of the monitoring wire contract — the
     * management frontend renders rows in this order.
     */
    private static final List<String> KNOWN_QUEUE_NAMES =
            List.of("judge_queue",
                    "notification_queue",
                    "email_queue");

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MetricsCollector metricsCollector;
    private final SystemProbe systemProbe;
    private final TimeSource timeSource;

    /**
     * The queue module's read port. Injecting this is the
     * monitoring → queue edge: monitoring consumes the queue module's
     * owned port (satisfies {@code .claude/rules/backend/06}) instead
     * of probing broker key layouts directly.
     */
    private final QueueHealthProbePort queueInspector;

    @Value("${spring.application.name:UltiCode}")
    private String applicationName;

    @Value("${app.version:1.0.0}")
    private String applicationVersion;

    @Value("${spring.profiles.active:development}")
    private String activeProfile;

    @Override
    @Cacheable(value = "monitoring", key = "'system'")
    public SystemInfoVO getSystemInfo() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        Properties props = System.getProperties();

        String hostname = "unknown";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            // broad catch: hostname resolution can fail in restricted containers; default keeps the response well-formed.
            log.warn("Could not determine hostname", e);
        }

        return SystemInfoVO.builder()
                .uptime(runtimeMXBean.getUptime() / 1000)
                .javaVersion(props.getProperty("java.version"))
                .platform(props.getProperty("os.name"))
                .hostname(hostname)
                .env(activeProfile)
                .pid(getProcessId())
                .version(applicationVersion)
                .build();
    }

    @Override
    @Cacheable(value = "monitoring", key = "'resources'")
    public ResourceUsageVO getResourceUsage() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        long heapUsed = memoryMXBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryMXBean.getHeapMemoryUsage().getMax();
        long nonHeapUsed = memoryMXBean.getNonHeapMemoryUsage().getUsed();

        return ResourceUsageVO.builder()
                .memory(ResourceUsageVO.MemoryInfo.builder()
                        .heapUsed(heapUsed)
                        .heapMax(heapMax)
                        .nonHeapUsed(nonHeapUsed)
                        .build())
                .cpu(ResourceUsageVO.CpuInfo.builder()
                        .processCpuLoad(systemProbe.processCpuLoad())
                        .systemCpuLoad(systemProbe.systemCpuLoad())
                        .availableProcessors(systemProbe.availableProcessors())
                        .build())
                .threadCount(threadMXBean.getThreadCount())
                .build();
    }

    @Override
    @Cacheable(value = "monitoring", key = "'database'")
    public DatabaseStatsVO getDatabaseStats() {
        int activeConnections = 0;
        int maxConnections = 0;
        String status = "healthy";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Get connection pool info
            ResultSet rs = stmt.executeQuery(
                    "SHOW STATUS WHERE Variable_name IN ('Threads_connected', 'Max_used_connections')");
            Map<String, Integer> stats = new HashMap<>();
            while (rs.next()) {
                stats.put(rs.getString("Variable_name"), rs.getInt("Value"));
            }
            activeConnections = stats.getOrDefault("Threads_connected", 0);

            // Get max connections setting
            ResultSet maxRs = stmt.executeQuery("SHOW VARIABLES LIKE 'max_connections'");
            if (maxRs.next()) {
                maxConnections = maxRs.getInt("Value");
            }

            status = "healthy";
        } catch (Exception e) {
            // broad catch: any JDBC failure surfaces as "unhealthy" without propagating.
            log.error("Failed to get database stats", e);
            status = "unhealthy";
        }

        return DatabaseStatsVO.builder()
                .activeConnections(activeConnections)
                .maxConnections(maxConnections)
                .queryCount(metricsCollector.getQueryCount())
                .slowQueries(metricsCollector.getSlowQueryCount())
                .status(status)
                .build();
    }

    @Override
    @Cacheable(value = "monitoring", key = "'queues'")
    public List<QueueStatsVO> getQueueStats() {
        List<QueueStatsVO> queues = new ArrayList<>();

        for (String queueName : KNOWN_QUEUE_NAMES) {
            QueueHealthSnapshotDTO snapshot = readQueueHealthSnapshot(queueName);
            queues.add(toQueueStatsVO(queueName, snapshot));
        }

        return queues;
    }

    @Override
    @Cacheable(value = "monitoring", key = "'redis'")
    public RedisStatsVO getRedisStats() {
        try {
            Properties info = redisTemplate.execute((RedisCallback<Properties>) connection -> connection.info());

            if (info == null) {
                return RedisStatsVO.builder()
                        .connected(false)
                        .build();
            }

            long usedMemory = parseMemory(info.getProperty("used_memory", "0"));
            int connectedClients = Integer.parseInt(info.getProperty("connected_clients", "0"));
            long uptime = Long.parseLong(info.getProperty("uptime_in_seconds", "0"));
            String version = info.getProperty("redis_version", "unknown");

            // Get total key count from db0
            long totalKeys = 0;
            String dbSize = info.getProperty("db0");
            if (dbSize != null && dbSize.contains("keys=")) {
                String keysPart = dbSize.split(",")[0];
                totalKeys = Long.parseLong(keysPart.replace("keys=", ""));
            }

            return RedisStatsVO.builder()
                    .connected(true)
                    .version(version)
                    .usedMemory(usedMemory)
                    .connectedClients(connectedClients)
                    .totalKeys(totalKeys)
                    .uptimeInSeconds(uptime)
                    .build();
        } catch (Exception e) {
            // broad catch: any Redis handshake failure becomes a "not connected" snapshot.
            log.error("Failed to get Redis stats", e);
            return RedisStatsVO.builder()
                    .connected(false)
                    .version("unknown")
                    .usedMemory(0L)
                    .connectedClients(0)
                    .totalKeys(0L)
                    .uptimeInSeconds(0L)
                    .build();
        }
    }

    @Override
    public SystemHealthVO getHealthCheck() {
        List<SystemHealthVO.HealthCheck> checks = new ArrayList<>();

        // Check database
        checks.add(checkDatabase());

        // Check Redis
        checks.add(checkRedis());

        // Check queues
        checks.add(checkQueues());

        // Determine overall status
        String overallStatus = determineOverallStatus(checks);

        return SystemHealthVO.builder()
                .status(overallStatus)
                .checks(checks)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Probe MySQL with {@code SELECT 1} and report latency.
     */
    private SystemHealthVO.HealthCheck checkDatabase() {
        long start = timeSource.wallMillis();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            long latency = timeSource.wallMillis() - start;
            return SystemHealthVO.HealthCheck.builder()
                    .service("database")
                    .status("healthy")
                    .latency(latency)
                    .message("Database responding normally")
                    .build();
        } catch (Exception e) {
            // broad catch: any JDBC failure surfaces as an unhealthy sub-check.
            long latency = timeSource.wallMillis() - start;
            return SystemHealthVO.HealthCheck.builder()
                    .service("database")
                    .status("unhealthy")
                    .latency(latency)
                    .message("Database connection failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Probe Redis with {@code PING} and report latency / response class.
     */
    private SystemHealthVO.HealthCheck checkRedis() {
        long start = timeSource.wallMillis();
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            long latency = timeSource.wallMillis() - start;
            if ("PONG".equalsIgnoreCase(pong)) {
                return SystemHealthVO.HealthCheck.builder()
                        .service("redis")
                        .status("healthy")
                        .latency(latency)
                        .message("Redis responding normally")
                        .build();
            } else {
                return SystemHealthVO.HealthCheck.builder()
                        .service("redis")
                        .status("degraded")
                        .latency(latency)
                        .message("Redis returned unexpected response: " + pong)
                        .build();
            }
        } catch (Exception e) {
            // broad catch: any Redis handshake failure is unhealthy.
            long latency = timeSource.wallMillis() - start;
            return SystemHealthVO.HealthCheck.builder()
                    .service("redis")
                    .status("unhealthy")
                    .latency(latency)
                    .message("Redis connection failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Read queue pressure via the queue inspector port and classify the
     * fleet. A probe failure on any single queue flips this check to
     * unhealthy — the failure is never folded into "queue empty and
     * healthy" (that was the original defect: a Redis outage reported
     * zero depth and a green check).
     */
    private SystemHealthVO.HealthCheck checkQueues() {
        long start = timeSource.wallMillis();
        boolean anyProbeFailed = false;
        boolean anySnapshotError = false;
        long failedJobs = 0L;
        int probed = 0;

        for (String queueName : KNOWN_QUEUE_NAMES) {
            try {
                QueueHealthSnapshotDTO snapshot = queueInspector.getQueueHealthSnapshot(queueName);
                probed++;
                if (snapshot.getProbeStatus() == ProbeStatus.PROBE_FAILED) {
                    anyProbeFailed = true;
                    log.warn("Queue probe failed for {} (reported PROBE_FAILED); surfacing as unhealthy",
                            queueName);
                } else {
                    failedJobs += snapshot.getFailedCount();
                }
            } catch (Exception e) {
                // broad catch: an unexpected exception (e.g. QUEUE_NOT_FOUND
                // from a typo, or a Spring infrastructure fault) is unhealthy
                // but reported separately from a broker PROBE_FAILED so the
                // operator can tell infrastructure failure from broker outage.
                anySnapshotError = true;
                log.warn("Queue snapshot threw for {}: {}", queueName, e.getMessage());
            }
        }

        long latency = timeSource.wallMillis() - start;

        if (anyProbeFailed || anySnapshotError) {
            return SystemHealthVO.HealthCheck.builder()
                    .service("queues")
                    .status("unhealthy")
                    .latency(latency)
                    .message("Queue probe failed for at least one queue "
                            + "(probeFailed=" + anyProbeFailed
                            + ", snapshotError=" + anySnapshotError + ")")
                    .build();
        }
        if (failedJobs > 100) {
            return SystemHealthVO.HealthCheck.builder()
                    .service("queues")
                    .status("degraded")
                    .latency(latency)
                    .message("High number of failed jobs: " + failedJobs)
                    .build();
        }
        return SystemHealthVO.HealthCheck.builder()
                .service("queues")
                .status("healthy")
                .latency(latency)
                .message("Queues operating normally (probed=" + probed + ")")
                .build();
    }

    /**
     * Reduce three sub-checks to a single overall status word.
     */
    private String determineOverallStatus(List<SystemHealthVO.HealthCheck> checks) {
        boolean hasUnhealthy = checks.stream()
                .anyMatch(c -> "unhealthy".equals(c.getStatus()));
        boolean hasDegraded = checks.stream()
                .anyMatch(c -> "degraded".equals(c.getStatus()));

        if (hasUnhealthy) {
            return "unhealthy";
        } else if (hasDegraded) {
            return "degraded";
        }
        return "healthy";
    }

    /**
     * Best-effort PID extraction from the JVM name ({@code "pid@host"}).
     *
     * @return the PID or {@code -1L} when the runtime name is not in
     *         the expected format
     */
    private Long getProcessId() {
        try {
            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            String jvmName = runtimeMXBean.getName();
            return Long.parseLong(jvmName.split("@")[0]);
        } catch (Exception e) {
            // broad catch: PID is a diagnostic nicety; -1L preserves the response shape.
            return -1L;
        }
    }

    /**
     * Parse a Redis INFO memory cell into a long, returning zero on
     * malformed input.
     */
    private long parseMemory(String memoryStr) {
        try {
            return Long.parseLong(memoryStr);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Read one queue's health snapshot via the queue inspector port,
     * translating an unexpected exception (not a probe failure) into
     * a synthetic PROBE_FAILED so the dashboard still renders the row
     * with zeros and the health check still flips unhealthy.
     */
    private QueueHealthSnapshotDTO readQueueHealthSnapshot(String queueName) {
        try {
            return queueInspector.getQueueHealthSnapshot(queueName);
        } catch (Exception e) {
            // broad catch: an unexpected exception outside the queue
            // inspector's own probe-failure handling still needs to
            // produce a dashboard row; surface it as PROBE_FAILED so
            // checkQueues() can flip unhealthy on this queue too.
            log.warn("Queue inspector threw for {}: {}", queueName, e.getMessage());
            return new QueueHealthSnapshotDTO(queueName, 0L, 0L, 0L, ProbeStatus.PROBE_FAILED);
        }
    }

    /**
     * Adapt the queue module's snapshot into the existing
     * {@link QueueStatsVO} wire shape so the management frontend
     * contract is unchanged. Fields the snapshot does not own yet
     * (active, delayed) stay at zero.
     *
     * <p>A PROBE_FAILED snapshot is rendered with zero depth here;
     * the unhealthy signal is surfaced separately by
     * {@link #checkQueues()} so the failure cannot be mistaken for
     * an empty-but-healthy queue.
     */
    private QueueStatsVO toQueueStatsVO(String queueName, QueueHealthSnapshotDTO snapshot) {
        return QueueStatsVO.builder()
                .name(queueName)
                .waiting(snapshot.getWaitingDepth())
                .active(0L)
                .completed(snapshot.getCompletedCount())
                .failed(snapshot.getFailedCount())
                .delayed(0L)
                .build();
    }
}
