package com.ulticode.modules.monitoring.service.impl;

import com.ulticode.modules.monitoring.dto.DatabaseStatsVO;
import com.ulticode.modules.monitoring.dto.QueueStatsVO;
import com.ulticode.modules.monitoring.dto.RedisStatsVO;
import com.ulticode.modules.monitoring.dto.ResourceUsageVO;
import com.ulticode.modules.monitoring.dto.SystemHealthVO;
import com.ulticode.modules.monitoring.dto.SystemInfoVO;
import com.ulticode.modules.monitoring.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Implementation of the MonitoringService interface.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringServiceImpl implements MonitoringService {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${spring.application.name:UltiCode}")
    private String applicationName;

    @Value("${app.version:1.0.0}")
    private String applicationVersion;

    @Value("${spring.profiles.active:development}")
    private String activeProfile;

    private volatile long queryCount = 0;

    @Override
    public SystemInfoVO getSystemInfo() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        Properties props = System.getProperties();

        String hostname = "unknown";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
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
    public ResourceUsageVO getResourceUsage() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        long heapUsed = memoryMXBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryMXBean.getHeapMemoryUsage().getMax();
        long nonHeapUsed = memoryMXBean.getNonHeapMemoryUsage().getUsed();

        double processCpuLoad = getProcessCpuLoad();
        double systemCpuLoad = getSystemCpuLoad(osMXBean);

        return ResourceUsageVO.builder()
                .memory(ResourceUsageVO.MemoryInfo.builder()
                        .heapUsed(heapUsed)
                        .heapMax(heapMax)
                        .nonHeapUsed(nonHeapUsed)
                        .build())
                .cpu(ResourceUsageVO.CpuInfo.builder()
                        .processCpuLoad(processCpuLoad)
                        .systemCpuLoad(systemCpuLoad)
                        .availableProcessors(osMXBean.getAvailableProcessors())
                        .build())
                .threadCount(threadMXBean.getThreadCount())
                .build();
    }

    @Override
    public DatabaseStatsVO getDatabaseStats() {
        int activeConnections = 0;
        int maxConnections = 0;
        String status = "healthy";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Get connection pool info
            ResultSet rs = stmt.executeQuery("SHOW STATUS WHERE Variable_name IN ('Threads_connected', 'Max_used_connections')");
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
            log.error("Failed to get database stats", e);
            status = "unhealthy";
        }

        return DatabaseStatsVO.builder()
                .activeConnections(activeConnections)
                .maxConnections(maxConnections)
                .queryCount(queryCount)
                .slowQueries(0) // Would require query logging to track
                .status(status)
                .build();
    }

    @Override
    public List<QueueStatsVO> getQueueStats() {
        List<QueueStatsVO> queues = new ArrayList<>();

        // Define known queue names for the application
        String[] queueNames = {"judge_queue", "notification_queue", "email_queue"};

        for (String queueName : queueNames) {
            try {
                QueueStatsVO queueStats = getQueueStats(queueName);
                queues.add(queueStats);
            // broad catch: any failure means service is unhealthy
            } catch (Exception e) {
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
    public RedisStatsVO getRedisStats() {
        try {
            Properties info = redisTemplate.execute((RedisCallback<Properties>) connection -> {
                return connection.info();
            });

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
        // broad catch: any failure means service is unhealthy
        } catch (Exception e) {
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
        // broad catch: any failure means service is unhealthy
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return SystemHealthVO.HealthCheck.builder()
                    .service("database")
                    .status("unhealthy")
                    .latency(latency)
                    .message("Database connection failed: " + e.getMessage())
                    .build();
        }
    }

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
        // broad catch: any failure means service is unhealthy
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return SystemHealthVO.HealthCheck.builder()
                    .service("redis")
                    .status("unhealthy")
                    .latency(latency)
                    .message("Redis connection failed: " + e.getMessage())
                    .build();
        }
    }

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
        // broad catch: any failure means service is unhealthy
        } catch (Exception e) {
            return SystemHealthVO.HealthCheck.builder()
                    .service("queues")
                    .status("unhealthy")
                    .latency(0L)
                    .message("Failed to check queues: " + e.getMessage())
                    .build();
        }
    }

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

    private Long getProcessId() {
        try {
            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            String jvmName = runtimeMXBean.getName();
            return Long.parseLong(jvmName.split("@")[0]);
        // broad catch: JVM runtime access may fail in restricted environments
        } catch (Exception e) {
            return -1L;
        }
    }

    private double getProcessCpuLoad() {
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            return osBean.getProcessCpuLoad();
        // broad catch: JVM runtime access may fail in restricted environments
        } catch (Exception e) {
            return -1.0;
        }
    }

    private double getSystemCpuLoad(OperatingSystemMXBean osMXBean) {
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                    (com.sun.management.OperatingSystemMXBean) osMXBean;
            return osBean.getCpuLoad();
        // broad catch: JVM runtime access may fail in restricted environments
        } catch (Exception e) {
            // Fallback to system load average
            double loadAverage = osMXBean.getSystemLoadAverage();
            int processors = osMXBean.getAvailableProcessors();
            if (loadAverage >= 0 && processors > 0) {
                return loadAverage / processors;
            }
            return -1.0;
        }
    }

    private long parseMemory(String memoryStr) {
        try {
            return Long.parseLong(memoryStr);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private QueueStatsVO getQueueStats(String queueName) {
        // Check BullMQ-style queue data in Redis
        String waitingKey = "bull:" + queueName + ":waiting";
        String activeKey = "bull:" + queueName + ":active";
        String completedKey = "bull:" + queueName + ":completed";
        String failedKey = "bull:" + queueName + ":failed";
        String delayedKey = "bull:" + queueName + ":delayed";

        Long waiting = getKeyCount(waitingKey);
        Long active = getKeyCount(activeKey);
        Long completed = getListLength(completedKey);
        Long failed = getListLength(failedKey);
        Long delayed = getKeyCount(delayedKey);

        return QueueStatsVO.builder()
                .name(queueName)
                .waiting(waiting != null ? waiting : 0L)
                .active(active != null ? active : 0L)
                .completed(completed != null ? completed : 0L)
                .failed(failed != null ? failed : 0L)
                .delayed(delayed != null ? delayed : 0L)
                .build();
    }

    private Long getKeyCount(String key) {
        try {
            return redisTemplate.execute((RedisCallback<Long>) connection -> {
                Long size = connection.setCommands().sCard(key.getBytes());
                return size != null ? size : 0L;
            });
        // broad catch: Redis operation failure returns default
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getListLength(String key) {
        try {
            Long length = redisTemplate.execute((RedisCallback<Long>) connection -> {
                return connection.listCommands().lLen(key.getBytes());
            });
            return length != null ? length : 0L;
        // broad catch: Redis operation failure returns default
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * Increment query count (can be called by query interceptors).
     */
    public void incrementQueryCount() {
        queryCount++;
    }
}
