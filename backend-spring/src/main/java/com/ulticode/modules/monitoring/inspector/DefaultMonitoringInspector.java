package com.ulticode.modules.monitoring.inspector;

import com.ulticode.common.metrics.MetricsCollector;
import com.ulticode.common.system.SystemProbe;
import com.ulticode.modules.monitoring.dto.DatabaseStatsVO;
import com.ulticode.modules.monitoring.dto.QueueStatsVO;
import com.ulticode.modules.monitoring.dto.RedisStatsVO;
import com.ulticode.modules.monitoring.dto.ResourceUsageVO;
import com.ulticode.modules.monitoring.dto.SystemHealthVO;
import com.ulticode.modules.monitoring.dto.SystemInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
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
 * reads from the JVM MXBeans, the {@code DataSource}, and the
 * {@code RedisTemplate} only.
 *
 * <p>Owns its own copy of the inspection logic — no inheritance from
 * any prior {@code MonitoringServiceImpl} class — so this read module
 * is independent of any write-path bean graph (none exists for the
 * monitoring subsystem; reads are the entire contract).
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
@RequiredArgsConstructor
public class DefaultMonitoringInspector implements MonitoringInspector {

    /** Static configuration: the BullMQ-style queue buckets this app runs. */
    private static final List<String> KNOWN_QUEUE_NAMES =
            List.of("judge_queue", "notification_queue", "email_queue");

    /** BullMQ key suffix: a Set holding the IDs of waiting jobs. */
    private static final String BULL_WAITING_SUFFIX = ":waiting";
    /** BullMQ key suffix: a Set holding the IDs of currently-active jobs. */
    private static final String BULL_ACTIVE_SUFFIX = ":active";
    /** BullMQ key suffix: a List holding completed-job metadata. */
    private static final String BULL_COMPLETED_SUFFIX = ":completed";
    /** BullMQ key suffix: a List holding failed-job metadata. */
    private static final String BULL_FAILED_SUFFIX = ":failed";
    /** BullMQ key suffix: a Set holding the IDs of delayed jobs. */
    private static final String BULL_DELAYED_SUFFIX = ":delayed";

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MetricsCollector metricsCollector;
    private final SystemProbe systemProbe;

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
            try {
                queues.add(inspectQueueBucket(queueName));
            } catch (Exception e) {
                // broad catch: any single failure must not blank the dashboard; preserve the rest of the fleet.
                log.warn("Could not get stats for queue: {}", queueName, e);
                queues.add(QueueStatsVO.builder()
                        .name(queueName)
                        .waiting(0L)
                        .active(0L)
                        .completed(0L)
                        .failed(0L)
                        .delayed(0L)
                        .build());
            }
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
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            long latency = System.currentTimeMillis() - start;
            return SystemHealthVO.HealthCheck.builder()
                    .service("database")
                    .status("healthy")
                    .latency(latency)
                    .message("Database responding normally")
                    .build();
        } catch (Exception e) {
            // broad catch: any JDBC failure surfaces as an unhealthy sub-check.
            long latency = System.currentTimeMillis() - start;
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
        long start = System.currentTimeMillis();
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            long latency = System.currentTimeMillis() - start;
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
            long latency = System.currentTimeMillis() - start;
            return SystemHealthVO.HealthCheck.builder()
                    .service("redis")
                    .status("unhealthy")
                    .latency(latency)
                    .message("Redis connection failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Read queue pressure and classify the fleet.
     */
    private SystemHealthVO.HealthCheck checkQueues() {
        try {
            List<QueueStatsVO> queues = getQueueStats();
            long failedJobs = queues.stream()
                    .mapToLong(QueueStatsVO::getFailed)
                    .sum();

            if (failedJobs > 100) {
                return SystemHealthVO.HealthCheck.builder()
                        .service("queues")
                        .status("degraded")
                        .latency(0L)
                        .message("High number of failed jobs: " + failedJobs)
                        .build();
            }
            return SystemHealthVO.HealthCheck.builder()
                    .service("queues")
                    .status("healthy")
                    .latency(0L)
                    .message("Queues operating normally")
                    .build();
        } catch (Exception e) {
            // broad catch: any inspection failure is unhealthy.
            return SystemHealthVO.HealthCheck.builder()
                    .service("queues")
                    .status("unhealthy")
                    .latency(0L)
                    .message("Failed to check queues: " + e.getMessage())
                    .build();
        }
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
     * Read the five BullMQ-style bucket sizes for one queue and
     * fold them into a single {@link QueueStatsVO}.
     */
    private QueueStatsVO inspectQueueBucket(String queueName) {
        String bullPrefix = "bull:" + queueName;

        Long waiting = getKeyCount(bullPrefix + BULL_WAITING_SUFFIX);
        Long active = getKeyCount(bullPrefix + BULL_ACTIVE_SUFFIX);
        Long completed = getListLength(bullPrefix + BULL_COMPLETED_SUFFIX);
        Long failed = getListLength(bullPrefix + BULL_FAILED_SUFFIX);
        Long delayed = getKeyCount(bullPrefix + BULL_DELAYED_SUFFIX);

        return QueueStatsVO.builder()
                .name(queueName)
                .waiting(waiting != null ? waiting : 0L)
                .active(active != null ? active : 0L)
                .completed(completed != null ? completed : 0L)
                .failed(failed != null ? failed : 0L)
                .delayed(delayed != null ? delayed : 0L)
                .build();
    }

    /**
     * Read a Set cardinality via {@code SCARD} on the given key.
     *
     * @return the cardinality or {@code 0L} when the key is missing,
     *         the command throws, or the connection is down.
     */
    private Long getKeyCount(String key) {
        try {
            return redisTemplate.execute((RedisCallback<Long>) connection -> {
                Long size = connection.setCommands().sCard(key.getBytes());
                return size != null ? size : 0L;
            });
        } catch (Exception e) {
            // broad catch: a Redis probe failure should not blank the queue stats.
            return 0L;
        }
    }

    /**
     * Read a List length via {@code LLEN} on the given key.
     *
     * @return the length or {@code 0L} when the key is missing, the
     *         command throws, or the connection is down.
     */
    private Long getListLength(String key) {
        try {
            Long length = redisTemplate.execute((RedisCallback<Long>) connection ->
                    connection.listCommands().lLen(key.getBytes()));
            return length != null ? length : 0L;
        } catch (Exception e) {
            // broad catch: same defensive posture as getKeyCount.
            return 0L;
        }
    }
}
