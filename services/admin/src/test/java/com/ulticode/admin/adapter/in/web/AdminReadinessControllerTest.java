package com.ulticode.admin.adapter.in.web;

import com.ulticode.common.storage.StorageReadiness;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Readiness gate for the Admin owner.
 *
 * <p>Admin writes backup objects, so the recorded object-store startup-gate outcome is part of the probe and an
 * unverified store answers 503.
 */
@DisplayName("AdminReadinessController")
class AdminReadinessControllerTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<DataSource> dataSourceProvider() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        ObjectProvider<DataSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(dataSource);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<StringRedisTemplate> redisProvider() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doReturn("PONG").when(redisTemplate).execute(any(RedisCallback.class));
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<StorageReadiness> readinessProvider(StorageReadiness readiness) {
        ObjectProvider<StorageReadiness> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(readiness);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> components(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("components");
    }

    @Test
    @DisplayName("reports the verified object store and answers 200")
    void reportsVerifiedStorage() throws Exception {
        StorageReadiness readiness = new StorageReadiness();
        readiness.markReady();
        AdminReadinessController controller = new AdminReadinessController(
                dataSourceProvider(), redisProvider(), readinessProvider(readiness));

        ResponseEntity<Map<String, Object>> response = controller.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(components(response)).containsEntry("storage", "UP");
    }

    @Test
    @DisplayName("runtime storage failure answers 503 and recovery answers 200")
    void runtimeStorageFailureAndRecoveryUpdatesReadiness() throws Exception {
        StorageReadiness readiness = new StorageReadiness();
        readiness.markFailed("runtime outage");
        AdminReadinessController controller = new AdminReadinessController(
                dataSourceProvider(), redisProvider(), readinessProvider(readiness));

        ResponseEntity<Map<String, Object>> failedResponse = controller.ready();

        assertThat(failedResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(components(failedResponse)).containsEntry("storage", "DOWN");

        readiness.markReady();

        ResponseEntity<Map<String, Object>> recoveredResponse = controller.ready();

        assertThat(recoveredResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(components(recoveredResponse)).containsEntry("storage", "UP");
    }

    @Test
    @DisplayName("a context without the shared storage module keeps db/redis semantics")
    void absentStorageModuleIsNotReportedDown() throws Exception {
        AdminReadinessController controller = new AdminReadinessController(
                dataSourceProvider(), redisProvider(), readinessProvider(null));

        ResponseEntity<Map<String, Object>> response = controller.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(components(response)).containsEntry("storage", "UP");
    }
}
