package com.ulticode.modules.monitoring.service;

import com.ulticode.common.metrics.MetricsCollector;
import com.ulticode.modules.monitoring.dto.DatabaseStatsVO;
import com.ulticode.modules.monitoring.dto.QueueStatsVO;
import com.ulticode.modules.monitoring.dto.RedisStatsVO;
import com.ulticode.modules.monitoring.dto.ResourceUsageVO;
import com.ulticode.modules.monitoring.dto.SystemHealthVO;
import com.ulticode.modules.monitoring.dto.SystemInfoVO;
import com.ulticode.modules.monitoring.service.impl.MonitoringServiceImpl;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MonitoringService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MonitoringServiceTest {

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

    @InjectMocks
    private MonitoringServiceImpl monitoringService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(monitoringService, "applicationName", "UltiCode");
        ReflectionTestUtils.setField(monitoringService, "applicationVersion", "1.0.0");
        ReflectionTestUtils.setField(monitoringService, "activeProfile", "test");
    }

    @Nested
    @DisplayName("getSystemInfo Tests")
    class GetSystemInfoTests {

        @Test
        @DisplayName("should return system info with correct fields")
        void shouldReturnSystemInfoWithCorrectFields() {
            // Act
            SystemInfoVO result = monitoringService.getSystemInfo();

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
            SystemInfoVO result = monitoringService.getSystemInfo();

            // Assert
            assertNotNull(result.getHostname());
        }

        @Test
        @DisplayName("should return valid process ID")
        void shouldReturnValidProcessId() {
            // Act
            SystemInfoVO result = monitoringService.getSystemInfo();

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
            ResourceUsageVO result = monitoringService.getResourceUsage();

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
            ResourceUsageVO result = monitoringService.getResourceUsage();

            // Assert
            assertNotNull(result.getCpu());
            assertNotNull(result.getCpu().getAvailableProcessors());
            assertTrue(result.getCpu().getAvailableProcessors() > 0);
        }

        @Test
        @DisplayName("should return thread count")
        void shouldReturnThreadCount() {
            // Act
            ResourceUsageVO result = monitoringService.getResourceUsage();

            // Assert
            assertNotNull(result.getThreadCount());
            assertTrue(result.getThreadCount() > 0);
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
            DatabaseStatsVO result = monitoringService.getDatabaseStats();

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
            DatabaseStatsVO result = monitoringService.getDatabaseStats();

            // Assert
            assertNotNull(result);
            assertEquals("unhealthy", result.getStatus());
        }
    }

    @Nested
    @DisplayName("getQueueStats Tests")
    class GetQueueStatsTests {

        @Test
        @DisplayName("should return queue stats for all queues")
        void shouldReturnQueueStatsForAllQueues() {
            // Arrange
            when(redisTemplate.execute(any(RedisCallback.class)))
                    .thenReturn(0L);

            // Act
            List<QueueStatsVO> result = monitoringService.getQueueStats();

            // Assert
            assertNotNull(result);
            assertFalse(result.isEmpty());
            assertTrue(result.stream().anyMatch(q -> "judge_queue".equals(q.getName())));
        }

        @Test
        @DisplayName("should return zero values when Redis is unavailable")
        void shouldReturnZeroValuesWhenRedisIsUnavailable() {
            // Arrange
            when(redisTemplate.execute(any(RedisCallback.class)))
                    .thenThrow(new RuntimeException("Redis unavailable"));

            // Act
            List<QueueStatsVO> result = monitoringService.getQueueStats();

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
            RedisStatsVO result = monitoringService.getRedisStats();

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
            RedisStatsVO result = monitoringService.getRedisStats();

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
            RedisStatsVO result = monitoringService.getRedisStats();

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
            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(0L);

            // Act
            SystemHealthVO result = monitoringService.getHealthCheck();

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
            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(0L);

            // Act
            SystemHealthVO result = monitoringService.getHealthCheck();

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
            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(0L);

            // Act
            SystemHealthVO result = monitoringService.getHealthCheck();

            // Assert
            List<String> serviceNames = result.getChecks().stream()
                    .map(SystemHealthVO.HealthCheck::getService)
                    .toList();
            assertTrue(serviceNames.contains("database"));
            assertTrue(serviceNames.contains("redis"));
            assertTrue(serviceNames.contains("queues"));
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
            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(0L);

            // Act
            SystemHealthVO result = monitoringService.getHealthCheck();

            // Assert
            assertEquals("degraded", result.getStatus());

            SystemHealthVO.HealthCheck redisCheck = result.getChecks().stream()
                    .filter(c -> "redis".equals(c.getService()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(redisCheck);
            assertEquals("degraded", redisCheck.getStatus());
        }
    }

    @Nested
    @DisplayName("incrementQueryCount Tests")
    class IncrementQueryCountTests {

        @Test
        @DisplayName("should increment query count without throwing")
        void shouldIncrementQueryCountWithoutThrowing() {
            // Act & Assert - just verify no exception is thrown
            assertDoesNotThrow(() -> {
                monitoringService.incrementQueryCount();
                monitoringService.incrementQueryCount();
                monitoringService.incrementQueryCount();
            });
        }

        @Test
        @DisplayName("deprecated incrementQueryCount delegates to MetricsCollector")
        void deprecatedIncrementDelegatesToMetricsCollector() {
            monitoringService.incrementQueryCount();
            monitoringService.incrementQueryCount();
            verify(metricsCollector, times(2)).incrementQuery();
        }
    }

    @Nested
    @DisplayName("MetricsCollector Integration Tests")
    class MetricsCollectorIntegrationTests {

        @Test
        @DisplayName("getDatabaseStats reports the current MetricsCollector query count")
        void getDatabaseStatsReportsQueryCountFromCollector() throws Exception {
            // Arrange: simulate the interceptor having counted 42 queries
            when(metricsCollector.getQueryCount()).thenReturn(42L);
            when(metricsCollector.getSlowQueryCount()).thenReturn(7L);
            // Stub the JDBC connection used for SHOW STATUS / SHOW VARIABLES
            Connection conn = mock(Connection.class);
            Statement stmt = mock(Statement.class);
            ResultSet rs = mock(ResultSet.class);
            when(dataSource.getConnection()).thenReturn(conn);
            when(conn.createStatement()).thenReturn(stmt);
            when(stmt.executeQuery(anyString())).thenReturn(rs);
            when(rs.next()).thenReturn(false);

            // Act
            DatabaseStatsVO result = monitoringService.getDatabaseStats();

            // Assert
            assertEquals(42L, result.getQueryCount());
            assertEquals(7, result.getSlowQueries());
        }
    }
}
