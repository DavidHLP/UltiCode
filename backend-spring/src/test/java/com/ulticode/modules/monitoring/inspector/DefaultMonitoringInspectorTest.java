package com.ulticode.modules.monitoring.inspector;

import com.ulticode.common.metrics.MetricsCollector;
import com.ulticode.common.system.SystemProbe;
import com.ulticode.common.time.FakeTimeSource;
import com.ulticode.modules.monitoring.dto.DatabaseStatsVO;
import com.ulticode.modules.monitoring.dto.QueueStatsVO;
import com.ulticode.modules.monitoring.dto.RedisStatsVO;
import com.ulticode.modules.monitoring.dto.ResourceUsageVO;
import com.ulticode.modules.monitoring.dto.SystemHealthVO;
import com.ulticode.modules.monitoring.dto.SystemInfoVO;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.dto.ProbeStatus;
import com.ulticode.modules.queue.dto.QueueHealthSnapshotDTO;
import com.ulticode.modules.queue.inspector.QueueInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultMonitoringInspector}. The inspector
 * is the new home for system info, resource usage, database stats,
 * queue stats, Redis stats, and aggregate health probe; tests here
 * mirror what {@code MonitoringServiceTest} used to cover so the
 * existing behavioural surface is preserved at the inspector seam.
 *
 * <p>Cache annotations on the implementation are intentionally not
 * exercised here — the unit under test is the read module, not the
 * Spring caching layer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultMonitoringInspectorTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisConnection redisConnection;

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private SystemProbe systemProbe;

    @Mock
    private QueueInspector queueInspector;

    private FakeTimeSource fakeTime;

    @InjectMocks
    private DefaultMonitoringInspector monitoringInspector;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(monitoringInspector, "applicationName", "UltiCode");
        ReflectionTestUtils.setField(monitoringInspector, "applicationVersion", "1.0.0");
        ReflectionTestUtils.setField(monitoringInspector, "activeProfile", "test");

        // Pin SystemProbe to deterministic values so the resource-usage
        // assertions (availableProcessors > 0) hold regardless of the
        // host JVM the test runs on.
        lenient().when(systemProbe.availableProcessors()).thenReturn(4);
        lenient().when(systemProbe.processCpuLoad()).thenReturn(0.42);
        lenient().when(systemProbe.systemCpuLoad()).thenReturn(0.55);

        // Pin TimeSource to a FakeTimeSource so health-probe latency is
        // deterministic; the inspector was migrated to read wall time
        // through the TimeSource seam in the architecture sweep.
        fakeTime = new FakeTimeSource(1_700_000_000_000L, 0L);
        ReflectionTestUtils.setField(monitoringInspector, "timeSource", fakeTime);

        // Default the queue inspector to OK/zero for every queue so health-check
        // tests can focus on database/redis behavior without re-stubbing each
        // queue. Tests that need to assert on queue behavior override these.
        lenient().when(queueInspector.getQueueHealthSnapshot(anyString()))
                .thenAnswer(inv -> QueueHealthSnapshotDTO.builder()
                        .queueName(inv.getArgument(0))
                        .waitingDepth(0L)
                        .failedCount(0L)
                        .completedCount(0L)
                        .probeStatus(ProbeStatus.OK)
                        .build());
    }

    @Nested
    @DisplayName("getSystemInfo Tests")
    class GetSystemInfoTests {

        @Test
        @DisplayName("should return system info with correct fields")
        void shouldReturnSystemInfoWithCorrectFields() {
            // Act
            SystemInfoVO result = monitoringInspector.getSystemInfo();

            // Assert
            assertNotNull(result);
            assertNotNull(result.getJavaVersion());
            assertNotNull(result.getPlatform());
            assertEquals("test", result.getEnv());
            assertEquals("1.0.0", result.getVersion());
            assertNotNull(result.getUptime());
        }

        @Test
        @DisplayName("should return non-null hostname")
        void shouldReturnNonNullHostname() {
            // Act
            SystemInfoVO result = monitoringInspector.getSystemInfo();

            // Assert
            assertNotNull(result.getHostname());
        }

        @Test
        @DisplayName("should return valid process ID")
        void shouldReturnValidProcessId() {
            // Act
            SystemInfoVO result = monitoringInspector.getSystemInfo();

            // Assert
            assertNotNull(result.getPid());
            assertTrue(result.getPid() >= -1);
        }
    }

    @Nested
    @DisplayName("getResourceUsage Tests")
    class GetResourceUsageTests {

        @Test
        @DisplayName("should return resource usage with memory info")
        void shouldReturnResourceUsageWithMemoryInfo() {
            // Act
            ResourceUsageVO result = monitoringInspector.getResourceUsage();

            // Assert
            assertNotNull(result);
            assertNotNull(result.getMemory());
            assertNotNull(result.getMemory().getHeapUsed());
            assertNotNull(result.getMemory().getHeapMax());
            assertNotNull(result.getMemory().getNonHeapUsed());
        }

        @Test
        @DisplayName("should return resource usage with CPU info")
        void shouldReturnResourceUsageWithCpuInfo() {
            // Act
            ResourceUsageVO result = monitoringInspector.getResourceUsage();

            // Assert
            assertNotNull(result.getCpu());
            assertNotNull(result.getCpu().getAvailableProcessors());
            assertTrue(result.getCpu().getAvailableProcessors() > 0);
        }

        @Test
        @DisplayName("should return thread count")
        void shouldReturnThreadCount() {
            // Act
            ResourceUsageVO result = monitoringInspector.getResourceUsage();

            // Assert
            assertNotNull(result.getThreadCount());
            assertTrue(result.getThreadCount() > 0);
        }

        @Test
        @DisplayName("should pull processor count from the SystemProbe seam")
        void shouldPullProcessorCountFromSystemProbe() {
            ResourceUsageVO result = monitoringInspector.getResourceUsage();

            assertEquals(4, result.getCpu().getAvailableProcessors());
        }

        @Test
        @DisplayName("should pull CPU loads from the SystemProbe seam")
        void shouldPullCpuLoadsFromSystemProbe() {
            ResourceUsageVO result = monitoringInspector.getResourceUsage();

            assertEquals(0.42, result.getCpu().getProcessCpuLoad());
            assertEquals(0.55, result.getCpu().getSystemCpuLoad());
        }

        @Test
        @DisplayName("should render -1.0 when SystemProbe reports CPU unavailable")
        void shouldRenderMinusOneWhenSystemProbeCpuUnavailable() {
            when(systemProbe.processCpuLoad()).thenReturn(-1.0);
            when(systemProbe.systemCpuLoad()).thenReturn(-1.0);

            ResourceUsageVO result = monitoringInspector.getResourceUsage();

            assertEquals(-1.0, result.getCpu().getProcessCpuLoad());
            assertEquals(-1.0, result.getCpu().getSystemCpuLoad());
        }
    }

    @Nested
    @DisplayName("getDatabaseStats Tests")
    class GetDatabaseStatsTests {

        @Test
        @DisplayName("should return healthy status when database is accessible")
        void shouldReturnHealthyStatusWhenDatabaseIsAccessible() throws Exception {
            // Arrange
            Connection mockConnection = mock(Connection.class);
            Statement mockStatement = mock(Statement.class);
            ResultSet mockResultSet = mock(ResultSet.class);
            ResultSet mockMaxResultSet = mock(ResultSet.class);

            when(dataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.createStatement()).thenReturn(mockStatement);
            when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);
            when(mockStatement.executeQuery(contains("max_connections"))).thenReturn(mockMaxResultSet);

            when(mockResultSet.next()).thenReturn(true, true, false);
            when(mockResultSet.getString("Variable_name")).thenReturn("Threads_connected");
            when(mockResultSet.getInt("Value")).thenReturn(5);

            when(mockMaxResultSet.next()).thenReturn(true, false);
            when(mockMaxResultSet.getInt("Value")).thenReturn(100);

            // Act
            DatabaseStatsVO result = monitoringInspector.getDatabaseStats();

            // Assert
            assertNotNull(result);
            assertEquals("healthy", result.getStatus());
            assertEquals(100, result.getMaxConnections());
        }

        @Test
        @DisplayName("should return unhealthy status when database connection fails")
        void shouldReturnUnhealthyStatusWhenDatabaseConnectionFails() throws Exception {
            // Arrange
            when(dataSource.getConnection()).thenThrow(new RuntimeException("Connection failed"));

            // Act
            DatabaseStatsVO result = monitoringInspector.getDatabaseStats();

            // Assert
            assertNotNull(result);
            assertEquals("unhealthy", result.getStatus());
        }

        @Test
        @DisplayName("should fold MetricsCollector query counts into database stats")
        void shouldFoldMetricsCollectorQueryCountsIntoDatabaseStats() throws Exception {
            // Arrange: interceptor side has counted 42 / 7 slow queries.
            when(metricsCollector.getQueryCount()).thenReturn(42L);
            when(metricsCollector.getSlowQueryCount()).thenReturn(7L);
            // JDBC stubs to keep the connection branch alive.
            Connection conn = mock(Connection.class);
            Statement stmt = mock(Statement.class);
            ResultSet rs = mock(ResultSet.class);
            when(dataSource.getConnection()).thenReturn(conn);
            when(conn.createStatement()).thenReturn(stmt);
            when(stmt.executeQuery(anyString())).thenReturn(rs);
            when(rs.next()).thenReturn(false);

            // Act
            DatabaseStatsVO result = monitoringInspector.getDatabaseStats();

            // Assert
            assertEquals(42L, result.getQueryCount());
            assertEquals(7, result.getSlowQueries());
        }
    }

    @Nested
    @DisplayName("getQueueStats Tests")
    class GetQueueStatsTests {

        @Test
        @DisplayName("should adapt the queue inspector snapshot for all known queues")
        void shouldAdaptQueueInspectorSnapshotForAllKnownQueues() {
            // Arrange — the queue inspector returns the real depth; monitoring
            // adapts it into the existing QueueStatsVO wire shape.
            when(queueInspector.getQueueHealthSnapshot(QueueConstants.JUDGE_QUEUE))
                    .thenReturn(snapshot(QueueConstants.JUDGE_QUEUE, 7L, ProbeStatus.OK));
            when(queueInspector.getQueueHealthSnapshot(QueueConstants.NOTIFICATION_QUEUE))
                    .thenReturn(snapshot(QueueConstants.NOTIFICATION_QUEUE, 3L, ProbeStatus.OK));
            when(queueInspector.getQueueHealthSnapshot(QueueConstants.EMAIL_QUEUE))
                    .thenReturn(snapshot(QueueConstants.EMAIL_QUEUE, 0L, ProbeStatus.OK));

            // Act
            List<QueueStatsVO> result = monitoringInspector.getQueueStats();

            // Assert
            assertNotNull(result);
            assertEquals(3, result.size());
            QueueStatsVO judge = result.stream()
                    .filter(q -> QueueConstants.JUDGE_QUEUE.equals(q.getName()))
                    .findFirst().orElse(null);
            assertNotNull(judge);
            assertEquals(7L, judge.getWaiting(),
                    "monitoring must surface the queue inspector's waitingDepth, not a BullMQ key cardinality");
        }

        @Test
        @DisplayName("should render zeros when the probe failed (unhealthy signal is on the health check)")
        void shouldRenderZerosWhenProbeFailed() {
            // Arrange — probe failure: depth is informational; the unhealthy signal
            // is surfaced separately by getHealthCheck() (see GetHealthCheckTests).
            when(queueInspector.getQueueHealthSnapshot(anyString()))
                    .thenReturn(snapshot("any", 0L, ProbeStatus.PROBE_FAILED));

            // Act
            List<QueueStatsVO> result = monitoringInspector.getQueueStats();

            // Assert
            assertNotNull(result);
            assertFalse(result.isEmpty());
            result.forEach(queue -> {
                assertEquals(0L, queue.getWaiting());
                assertEquals(0L, queue.getActive());
                assertEquals(0L, queue.getCompleted());
                assertEquals(0L, queue.getFailed());
                assertEquals(0L, queue.getDelayed());
            });
        }

        @Test
        @DisplayName("should not call redisTemplate for queue depth (regression: BullMQ probe deletion)")
        void shouldNotCallRedisTemplateForQueueDepth() {
            // Arrange — the queue inspector handles depth; monitoring must NOT
            // reach into redisTemplate for SCARD/LLEN. Verify by leaving
            // redisTemplate completely unstubbed for the queue path.
            when(queueInspector.getQueueHealthSnapshot(anyString()))
                    .thenReturn(snapshot("any", 0L, ProbeStatus.OK));

            // Act — must not throw and must not invoke redisTemplate
            List<QueueStatsVO> result = monitoringInspector.getQueueStats();

            // Assert — no exception, list returned; redisTemplate was not touched.
            assertNotNull(result);
            org.mockito.Mockito.verifyNoInteractions(redisTemplate);
        }

        private QueueHealthSnapshotDTO snapshot(String name, long depth, ProbeStatus status) {
            return QueueHealthSnapshotDTO.builder()
                    .queueName(name)
                    .waitingDepth(depth)
                    .failedCount(0L)
                    .completedCount(0L)
                    .probeStatus(status)
                    .build();
        }
    }

    @Nested
    @DisplayName("getRedisStats Tests")
    class GetRedisStatsTests {

        @Test
        @DisplayName("should return connected status when Redis is available")
        void shouldReturnConnectedStatusWhenRedisIsAvailable() {
            // Arrange
            Properties redisInfo = new Properties();
            redisInfo.setProperty("used_memory", "1024000");
            redisInfo.setProperty("connected_clients", "10");
            redisInfo.setProperty("uptime_in_seconds", "86400");
            redisInfo.setProperty("redis_version", "7.0.0");

            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(redisInfo);

            // Act
            RedisStatsVO result = monitoringInspector.getRedisStats();

            // Assert
            assertNotNull(result);
            assertTrue(result.getConnected());
            assertEquals("7.0.0", result.getVersion());
            assertEquals(1024000L, result.getUsedMemory());
            assertEquals(10, result.getConnectedClients());
            assertEquals(86400L, result.getUptimeInSeconds());
        }

        @Test
        @DisplayName("should return disconnected status when Redis is unavailable")
        void shouldReturnDisconnectedStatusWhenRedisIsUnavailable() {
            // Arrange
            when(redisTemplate.execute(any(RedisCallback.class)))
                    .thenThrow(new RuntimeException("Redis connection refused"));

            // Act
            RedisStatsVO result = monitoringInspector.getRedisStats();

            // Assert
            assertNotNull(result);
            assertFalse(result.getConnected());
            assertEquals("unknown", result.getVersion());
            assertEquals(0L, result.getUsedMemory());
            assertEquals(0, result.getConnectedClients());
        }

        @Test
        @DisplayName("should handle null Redis info response")
        void shouldHandleNullRedisInfoResponse() {
            // Arrange
            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);

            // Act
            RedisStatsVO result = monitoringInspector.getRedisStats();

            // Assert
            assertNotNull(result);
            assertFalse(result.getConnected());
        }
    }

    @Nested
    @DisplayName("getHealthCheck Tests")
    class GetHealthCheckTests {

        @Test
        @DisplayName("should return overall healthy status when all services are healthy")
        void shouldReturnOverallHealthyStatusWhenAllServicesAreHealthy() throws Exception {
            // Arrange - Mock database
            Connection mockConnection = mock(Connection.class);
            Statement mockStatement = mock(Statement.class);

            when(dataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.createStatement()).thenReturn(mockStatement);
            when(mockStatement.execute(anyString())).thenReturn(true);

            // Arrange - Mock Redis
            when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
            when(redisConnection.ping()).thenReturn("PONG");
            when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);

            // Act
            SystemHealthVO result = monitoringInspector.getHealthCheck();

            // Assert
            assertNotNull(result);
            assertNotNull(result.getTimestamp());
            assertNotNull(result.getChecks());
            assertEquals(3, result.getChecks().size());
        }

        @Test
        @DisplayName("should return unhealthy status when database is down")
        void shouldReturnUnhealthyStatusWhenDatabaseIsDown() throws Exception {
            // Arrange - Mock database failure
            lenient().when(dataSource.getConnection()).thenThrow(new RuntimeException("Database connection failed"));

            // Arrange - Mock Redis
            when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
            when(redisConnection.ping()).thenReturn("PONG");
            when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);

            // Act
            SystemHealthVO result = monitoringInspector.getHealthCheck();

            // Assert
            assertNotNull(result);
            assertEquals("unhealthy", result.getStatus());

            SystemHealthVO.HealthCheck dbCheck = result.getChecks().stream()
                    .filter(c -> "database".equals(c.getService()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(dbCheck);
            assertEquals("unhealthy", dbCheck.getStatus());
        }

        @Test
        @DisplayName("should include all service checks")
        void shouldIncludeAllServiceChecks() throws Exception {
            // Arrange - Mock database
            Connection mockConnection = mock(Connection.class);
            Statement mockStatement = mock(Statement.class);
            when(dataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.createStatement()).thenReturn(mockStatement);
            when(mockStatement.execute(anyString())).thenReturn(true);

            // Arrange - Mock Redis
            when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
            when(redisConnection.ping()).thenReturn("PONG");
            when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);

            // Act
            SystemHealthVO result = monitoringInspector.getHealthCheck();

            // Assert
            List<String> serviceNames = result.getChecks().stream()
                    .map(SystemHealthVO.HealthCheck::getService)
                    .toList();
            assertTrue(serviceNames.contains("database"));
            assertTrue(serviceNames.contains("redis"));
            assertTrue(serviceNames.contains("queues"));
        }

        @Test
        @DisplayName("should compute health-check latency from the TimeSource seam (database probe)")
        void shouldComputeLatencyFromTimeSourceForDatabase() throws Exception {
            // Arrange - database probe will run for 42ms of fake wall time
            fakeTime.pinWall(1_700_000_000_000L);
            Connection mockConnection = mock(Connection.class);
            Statement mockStatement = mock(Statement.class);
            when(dataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.createStatement()).thenReturn(mockStatement);
            org.mockito.Mockito.doAnswer(inv -> {
                fakeTime.advance(42L);
                return true;
            }).when(mockStatement).execute(anyString());

            when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
            when(redisConnection.ping()).thenReturn("PONG");
            when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);

            // Act
            SystemHealthVO result = monitoringInspector.getHealthCheck();

            // Assert
            SystemHealthVO.HealthCheck dbCheck = result.getChecks().stream()
                    .filter(c -> "database".equals(c.getService()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(dbCheck);
            assertEquals(42L, dbCheck.getLatency(),
                    "database latency must come from the TimeSource seam, not the wall clock");
        }

        @Test
        @DisplayName("should compute health-check latency from the TimeSource seam (redis probe)")
        void shouldComputeLatencyFromTimeSourceForRedis() throws Exception {
            // Arrange - redis probe will run for 17ms of fake wall time
            fakeTime.pinWall(1_700_000_000_000L);
            Connection mockConnection = mock(Connection.class);
            Statement mockStatement = mock(Statement.class);
            when(dataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.createStatement()).thenReturn(mockStatement);
            when(mockStatement.execute(anyString())).thenReturn(true);

            when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
            org.mockito.Mockito.doAnswer(inv -> {
                fakeTime.advance(17L);
                return "PONG";
            }).when(redisConnection).ping();
            when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);

            // Act
            SystemHealthVO result = monitoringInspector.getHealthCheck();

            // Assert
            SystemHealthVO.HealthCheck redisCheck = result.getChecks().stream()
                    .filter(c -> "redis".equals(c.getService()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(redisCheck);
            assertEquals(17L, redisCheck.getLatency(),
                    "redis latency must come from the TimeSource seam, not the wall clock");
        }

        @Test
        @DisplayName("should return degraded status when Redis returns unexpected response")
        void shouldReturnDegradedStatusWhenRedisReturnsUnexpectedResponse() throws Exception {
            // Arrange - Mock database
            Connection mockConnection = mock(Connection.class);
            Statement mockStatement = mock(Statement.class);
            when(dataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.createStatement()).thenReturn(mockStatement);
            when(mockStatement.execute(anyString())).thenReturn(true);

            // Arrange - Mock Redis with unexpected response
            when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
            when(redisConnection.ping()).thenReturn("UNEXPECTED");
            when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);

            // Act
            SystemHealthVO result = monitoringInspector.getHealthCheck();

            // Assert
            assertEquals("degraded", result.getStatus());

            SystemHealthVO.HealthCheck redisCheck = result.getChecks().stream()
                    .filter(c -> "redis".equals(c.getService()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(redisCheck);
            assertEquals("degraded", redisCheck.getStatus());
        }

        @Test
        @DisplayName("probe failure on any queue MUST flip the queues check to unhealthy (regression: original defect reported green during Redis outages)")
        void queueProbeFailureMustFlipCheckToUnhealthy() throws Exception {
            // Arrange — database and Redis are healthy; ONLY the queue probe fails.
            // Under the original (BullMQ) implementation, a Redis outage read as
            // zero depth on every queue and the check returned "healthy" with
            // latency pinned to 0L. The new design must fail closed.
            Connection mockConnection = mock(Connection.class);
            Statement mockStatement = mock(Statement.class);
            when(dataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.createStatement()).thenReturn(mockStatement);
            when(mockStatement.execute(anyString())).thenReturn(true);
            when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
            when(redisConnection.ping()).thenReturn("PONG");
            when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);

            // Override the default setUp stub: every queue reports PROBE_FAILED.
            when(queueInspector.getQueueHealthSnapshot(anyString()))
                    .thenAnswer(inv -> QueueHealthSnapshotDTO.builder()
                            .queueName(inv.getArgument(0))
                            .waitingDepth(0L)
                            .failedCount(0L)
                            .completedCount(0L)
                            .probeStatus(ProbeStatus.PROBE_FAILED)
                            .build());

            // Act
            SystemHealthVO result = monitoringInspector.getHealthCheck();

            // Assert — overall unhealthy, and the queues sub-check is unhealthy.
            assertEquals("unhealthy", result.getStatus(),
                    "a PROBE_FAILED snapshot must not be folded into zero-then-healthy");

            SystemHealthVO.HealthCheck queueCheck = result.getChecks().stream()
                    .filter(c -> "queues".equals(c.getService()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(queueCheck);
            assertEquals("unhealthy", queueCheck.getStatus());
            assertTrue(queueCheck.getLatency() >= 0L,
                    "queue health latency must be measured (not hard-pinned to 0L)");
        }

        @Test
        @DisplayName("an unexpected exception from the queue inspector also surfaces as unhealthy")
        void queueInspectorExceptionMustFlipCheckToUnhealthy() throws Exception {
            // Arrange — database and Redis healthy; the queue inspector throws
            // unexpectedly (e.g. Spring infrastructure fault). The check must
            // still flip unhealthy rather than blanking the dashboard.
            Connection mockConnection = mock(Connection.class);
            Statement mockStatement = mock(Statement.class);
            when(dataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.createStatement()).thenReturn(mockStatement);
            when(mockStatement.execute(anyString())).thenReturn(true);
            when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
            when(redisConnection.ping()).thenReturn("PONG");
            when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);
            when(queueInspector.getQueueHealthSnapshot(anyString()))
                    .thenThrow(new RuntimeException("inspector infrastructure fault"));

            // Act
            SystemHealthVO result = monitoringInspector.getHealthCheck();

            // Assert
            assertEquals("unhealthy", result.getStatus());
            SystemHealthVO.HealthCheck queueCheck = result.getChecks().stream()
                    .filter(c -> "queues".equals(c.getService()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(queueCheck);
            assertEquals("unhealthy", queueCheck.getStatus());
        }

        @Test
        @DisplayName("queue health-check latency is measured from the TimeSource seam (not hard-pinned to 0)")
        void queueHealthCheckLatencyComesFromTimeSource() throws Exception {
            // Arrange — make the queue inspector probe advance fake wall time,
            // mirroring how the database/redis latency tests pin the seam.
            fakeTime.pinWall(1_700_000_000_000L);
            Connection mockConnection = mock(Connection.class);
            Statement mockStatement = mock(Statement.class);
            when(dataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.createStatement()).thenReturn(mockStatement);
            when(mockStatement.execute(anyString())).thenReturn(true);
            when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
            when(redisConnection.ping()).thenReturn("PONG");
            when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);
            when(queueInspector.getQueueHealthSnapshot(anyString())).thenAnswer(inv -> {
                fakeTime.advance(11L);
                return QueueHealthSnapshotDTO.builder()
                        .queueName(inv.getArgument(0))
                        .waitingDepth(0L)
                        .probeStatus(ProbeStatus.OK)
                        .build();
            });

            SystemHealthVO result = monitoringInspector.getHealthCheck();

            SystemHealthVO.HealthCheck queueCheck = result.getChecks().stream()
                    .filter(c -> "queues".equals(c.getService()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(queueCheck);
            assertTrue(queueCheck.getLatency() >= 11L,
                    "queue latency must be measured via TimeSource, not hard-pinned to 0L");
        }
    }
}
