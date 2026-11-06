package com.ulticode.app.adapter.in.web;

import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.storage.StorageReadiness;
import com.ulticode.submission.api.service.SubmissionUserQueryPort;
import java.util.List;
import java.util.Set;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.reference.ReferenceBeanManager;
import org.apache.dubbo.registry.client.ServiceDiscoveryRegistryDirectory;
import org.apache.dubbo.registry.client.migration.MigrationInvoker;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.registry.client.ServiceInstance;
import org.apache.dubbo.registry.client.event.listener.ServiceInstancesChangedListener;
import org.apache.dubbo.rpc.cluster.router.state.BitList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Readiness gate for the App owner.
 *
 * <p>The object store is mandatory, so its recorded startup-gate outcome is part of the probe: an unverified store must
 * never look ready, while contexts without the shared module keep their previous behaviour.
 */
@DisplayName("AppReadinessController")
@ExtendWith(OutputCaptureExtension.class)
class AppReadinessControllerTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ReferenceBeanManager> referenceProvider(ReferenceBeanManager references) {
        ObjectProvider<ReferenceBeanManager> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(references);
        return provider;
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void discoveryDiagnosticsDoNotProbeOrChangeReadinessAndOnlyLogChanges(CapturedOutput output) throws Exception {
        ReferenceBeanManager manager = mock(ReferenceBeanManager.class);
        ReferenceBean reference = mock(ReferenceBean.class);
        ReferenceConfig config = mock(ReferenceConfig.class);
        MigrationInvoker invoker = mock(MigrationInvoker.class);
        ServiceDiscoveryRegistryDirectory directory = mock(ServiceDiscoveryRegistryDirectory.class);
        when(reference.getServiceInterface()).thenReturn(SubmissionUserQueryPort.class.getName());
        when(reference.getReferenceConfig()).thenReturn(config);
        when(config.getInvoker()).thenReturn(invoker);
        when(invoker.getDirectory()).thenReturn(directory);
        when(directory.getAllInvokers()).thenReturn(List.of());
        when(directory.getValidInvokers()).thenReturn(new BitList(List.of()));
        when(directory.getInvokersToReconnect()).thenReturn(List.of());
        when(directory.getDisabledInvokers()).thenReturn(Set.of());
        ClusterInvoker applicationInvoker = mock(ClusterInvoker.class);
        ServiceDiscoveryRegistryDirectory applicationDirectory = mock(ServiceDiscoveryRegistryDirectory.class);
        ServiceInstancesChangedListener listener = mock(ServiceInstancesChangedListener.class);
        when(invoker.getServiceDiscoveryInvoker()).thenReturn(applicationInvoker);
        when(applicationInvoker.getDirectory()).thenReturn(applicationDirectory);
        when(applicationDirectory.getAllInvokers()).thenReturn(List.of(mock(org.apache.dubbo.rpc.Invoker.class)));
        when(applicationDirectory.getValidInvokers()).thenReturn(new BitList(List.of()));
        when(applicationDirectory.getInvokersToReconnect()).thenReturn(List.of());
        when(applicationDirectory.getDisabledInvokers()).thenReturn(Set.of());
        when(applicationDirectory.getServiceListener()).thenReturn(listener);
        when(listener.getServiceNames()).thenReturn(Set.of("never-log-application-name"));
        when(listener.getAllInstances()).thenReturn(Map.of("never-log-application-name", List.of(mock(ServiceInstance.class))));
        doThrow(new AssertionError("must not probe application")).when(applicationInvoker).isAvailable();
        doThrow(new AssertionError("must not read application URL")).when(applicationInvoker).getUrl();
        doThrow(new AssertionError("must not invoke application RPC")).when(applicationInvoker).invoke(any());
        ReferenceBean authReference = mock(ReferenceBean.class);
        when(authReference.getServiceInterface()).thenReturn(AccountQueryService.class.getName());
        when(authReference.getReferenceConfig()).thenReturn(config);
        when(manager.getReferences()).thenReturn(List.of(reference, authReference));
        doThrow(new AssertionError("must not probe")).when(invoker).isAvailable();
        doThrow(new AssertionError("must not read URLs")).when(invoker).getUrl();
        doThrow(new AssertionError("must not invoke RPC")).when(invoker).invoke(any());
        doThrow(new AssertionError("must not probe directory")).when(directory).isAvailable();
        AppReadinessController controller = new AppReadinessController(
                dataSourceProvider(), pingingRedis(), readinessProvider(null), referenceProvider(manager));

        assertThat(controller.ready().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.ready().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(output.getAll()).contains("AppDiscoveryObservation interface=submission_user_query state=INVOKER_PRESENT invoker_kind=MIGRATION directory_kind=SERVICE_DISCOVERY destroyed=FALSE all=0 valid=0 reconnect=0 disabled=0");
        assertThat(output.getAll().split("AppDiscoveryObservation interface=submission_user_query", -1)).hasSize(2);
        assertThat(output.getAll()).contains("AppDiscoveryObservation interface=auth_account_query state=INVOKER_PRESENT");
        assertThat(output.getAll().split("AppDiscoveryObservation interface=auth_account_query", -1)).hasSize(2);
        assertThat(output.getAll()).contains("AppApplicationDiscoveryObservation interface=submission_user_query state=INVOKER_PRESENT invoker_kind=MIGRATION directory_kind=SERVICE_DISCOVERY destroyed=FALSE all=1 valid=0 reconnect=0 disabled=0 listener_state=PRESENT mapped_apps=1 observed_apps=1 instances=1")
                .doesNotContain("never-log-application-name");
        assertThat(output.getAll().split("AppApplicationDiscoveryObservation interface=submission_user_query", -1)).hasSize(2);
        assertThat(output.getAll().split("AppApplicationDiscoveryObservation interface=auth_account_query", -1)).hasSize(2);
        when(manager.getReferences()).thenThrow(new IllegalStateException("credential-canary"));
        assertThat(controller.ready().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(output.getAll()).contains("interface=submission_user_query state=INSPECTION_ERROR")
                .doesNotContain("credential-canary");
        verify(invoker, never()).isAvailable();
        verify(invoker, never()).getUrl();
        verify(invoker, never()).invoke(any());
        verify(directory, never()).isAvailable();
        verify(reference, never()).getObject();
        verify(authReference, never()).getObject();
        verify(applicationInvoker, never()).isAvailable();
        verify(applicationInvoker, never()).getUrl();
        verify(applicationInvoker, never()).invoke(any());
        verify(applicationDirectory, never()).isAvailable();
    }

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
                dataSourceProvider(), pingingRedis(), readinessProvider(readiness), referenceProvider(null));

        ResponseEntity<Map<String, Object>> response = controller.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(components(response)).containsEntry("storage", "UP");
    }

    @Test
    @DisplayName("runtime storage failure answers 503 and recovery answers 200")
    void runtimeStorageFailureAndRecoveryUpdatesReadiness() throws Exception {
        StorageReadiness readiness = new StorageReadiness();
        readiness.markFailed("runtime outage");
        AppReadinessController controller = new AppReadinessController(
                dataSourceProvider(), pingingRedis(), readinessProvider(readiness), referenceProvider(null));

        ResponseEntity<Map<String, Object>> failedResponse = controller.ready();

        assertThat(failedResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(components(failedResponse)).containsEntry("storage", "DOWN");
        assertThat(failedResponse.getBody()).containsEntry("status", "DOWN");

        readiness.markReady();

        ResponseEntity<Map<String, Object>> recoveredResponse = controller.ready();

        assertThat(recoveredResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(components(recoveredResponse)).containsEntry("storage", "UP");
    }

    @Test
    @DisplayName("a context without the shared storage module keeps db/redis semantics")
    void absentStorageModuleIsNotReportedDown() throws Exception {
        AppReadinessController controller = new AppReadinessController(
                dataSourceProvider(), pingingRedis(), readinessProvider(null), referenceProvider(null));

        ResponseEntity<Map<String, Object>> response = controller.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(components(response)).containsEntry("storage", "UP");
    }
}
