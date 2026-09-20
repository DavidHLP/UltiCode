package com.ulticode.app.adapter.in.web;

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
 * Readiness gate for the App owner.
 *
 * <p>The object store is mandatory, so its recorded startup-gate outcome is part of the probe: an unverified store must
 * never look ready, while contexts without the shared module keep their previous behaviour.
 */
@DisplayName("AppReadinessController")
class AppReadinessControllerTest {

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
    private static StringRedisTemplate pingingRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doReturn("PONG").when(redisTemplate).execute(any(RedisCallback.class));
        return redisTemplate;
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
        AppReadinessController controller = new AppReadinessController(
                dataSourceProvider(), pingingRedis(), readinessProvider(readiness));

        ResponseEntity<Map<String, Object>> response = controller.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(components(response)).containsEntry("storage", "UP");
    }

    @Test
    @DisplayName("an unverified object store answers 503")
    void unverifiedStorageIsNotReady() throws Exception {
        StorageReadiness readiness = new StorageReadiness();
        readiness.markFailed("probe exhausted");
        AppReadinessController controller = new AppReadinessController(
                dataSourceProvider(), pingingRedis(), readinessProvider(readiness));

        ResponseEntity<Map<String, Object>> response = controller.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(components(response)).containsEntry("storage", "DOWN");
        assertThat(response.getBody()).containsEntry("status", "DOWN");
    }

    @Test
    @DisplayName("a context without the shared storage module keeps db/redis semantics")
    void absentStorageModuleIsNotReportedDown() throws Exception {
        AppReadinessController controller = new AppReadinessController(
                dataSourceProvider(), pingingRedis(), readinessProvider(null));

        ResponseEntity<Map<String, Object>> response = controller.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(components(response)).containsEntry("storage", "UP");
    }
}
