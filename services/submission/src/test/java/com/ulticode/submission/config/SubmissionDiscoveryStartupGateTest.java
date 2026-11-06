package com.ulticode.submission.config;

import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.rpc.resilience.DubboDiscoveryReadiness;
import com.ulticode.rpc.resilience.DubboDiscoveryReadiness.State;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.apache.dubbo.registry.client.ServiceDiscoveryRegistryDirectory;
import org.apache.dubbo.registry.client.migration.MigrationInvoker;
import org.apache.dubbo.registry.client.migration.ServiceDiscoveryMigrationInvoker;
import org.apache.dubbo.registry.integration.DynamicDirectory;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.router.state.BitList;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.reference.ReferenceBeanManager;
import org.apache.dubbo.rpc.Invoker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SubmissionDiscoveryStartupGateTest {
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ReferenceBean reference(Class<?> type, boolean available) {
        ReferenceBean bean = mock(ReferenceBean.class);
        ReferenceConfig config = mock(ReferenceConfig.class);
        Invoker invoker = mock(Invoker.class);
        when(bean.getServiceInterface()).thenReturn(type.getName());
        when(bean.getReferenceConfig()).thenReturn(config);
        when(config.getInvoker()).thenReturn(invoker);
        when(invoker.isAvailable()).thenReturn(available);
        return bean;
    }

    @SuppressWarnings("unchecked")
    private static SubmissionDiscoveryStartupGate gate(ReferenceBeanManager manager) {
        ObjectProvider<ReferenceBeanManager> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(manager);
        return new SubmissionDiscoveryStartupGate(provider);
    }

    @Test
    void missingOrPartialDiscoveryFailsClosedWithoutInvokingACommand() {
        assertThat(gate(null).ready()).isFalse();
        var manager = mock(ReferenceBeanManager.class);
        var problem = reference(ProblemFactsPort.class, true);
        when(manager.getReferences()).thenReturn(List.of(problem));
        var gate = gate(manager);
        assertThat(gate.ready()).isFalse();
        assertThatThrownBy(() -> gate.await(Duration.ZERO)).isInstanceOf(IllegalStateException.class);
        verify(problem, never()).getObject();
    }

    @Test
    void bothProjectionProvidersMustBeAvailableAndCanRecover() throws Exception {
        var manager = mock(ReferenceBeanManager.class);
        var problem = reference(ProblemFactsPort.class, false);
        var users = reference(SubmissionUserReadPort.class, true);
        when(manager.getReferences()).thenReturn(List.of(problem, users));
        var gate = gate(manager);
        assertThat(gate.ready()).isFalse();
        when(problem.getReferenceConfig().getInvoker().isAvailable()).thenReturn(true);
        assertThat(gate.ready()).isTrue();
        gate.await(Duration.ZERO);
        verify(problem, never()).getObject();
        verify(users, never()).getObject();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ReferenceBeanManager diagnosticManager(Invoker invoker) {
        var manager = mock(ReferenceBeanManager.class);
        ReferenceBean bean = mock(ReferenceBean.class);
        ReferenceConfig config = mock(ReferenceConfig.class);
        when(bean.getServiceInterface()).thenReturn(ProblemFactsPort.class.getName());
        when(bean.getReferenceConfig()).thenReturn(config);
        when(config.getInvoker()).thenReturn(invoker);
        when(manager.getReferences()).thenReturn(List.of(bean));
        return manager;
    }

    private static DubboDiscoveryReadiness.Observation inspect(ReferenceBeanManager manager) {
        var result = DubboDiscoveryReadiness.inspect(manager, ProblemFactsPort.class.getName());
        for (var reference : manager.getReferences()) verify(reference, never()).getObject();
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void forbidProbe(Invoker invoker) {
        doThrow(new AssertionError("diagnostics must not probe availability")).when(invoker).isAvailable();
        doThrow(new AssertionError("diagnostics must not read URLs")).when(invoker).getUrl();
        doThrow(new AssertionError("diagnostics must not invoke RPC")).when(invoker).invoke(any());
    }

    @SuppressWarnings("rawtypes")
    private static void verifyNoProbe(Invoker invoker) {
        verify(invoker, never()).isAvailable();
        verify(invoker, never()).getUrl();
        verify(invoker, never()).invoke(any());
    }

    @Test
    void diagnosticMissingAndErrorStatesDoNotBecomeAvailabilityClaims() {
        String type = ProblemFactsPort.class.getName();
        assertThat(DubboDiscoveryReadiness.inspect(null, type).state()).isEqualTo(State.MANAGER_MISSING);
        var manager = mock(ReferenceBeanManager.class);
        when(manager.getReferences()).thenReturn(List.of());
        assertThat(inspect(manager).state()).isEqualTo(State.REFERENCE_MISSING);
        manager = diagnosticManager(null);
        var reference = manager.getReferences().iterator().next();
        assertThat(inspect(manager).state()).isEqualTo(State.INVOKER_MISSING);
        when(reference.getReferenceConfig()).thenReturn(null);
        assertThat(inspect(manager).state()).isEqualTo(State.CONFIG_MISSING);
        when(reference.getReferenceConfig()).thenThrow(new IllegalStateException("credential-canary"));
        assertThat(inspect(manager).state()).isEqualTo(State.INSPECTION_ERROR);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void unknownImplementationsAreUnsupportedWithoutInvokingTheirGetters() {
        Invoker other = mock(Invoker.class);
        forbidProbe(other);
        var observation = inspect(diagnosticManager(other));
        assertThat(observation.state()).isEqualTo(State.UNSUPPORTED);
        assertThat(observation.invokerKind()).isEqualTo(DubboDiscoveryReadiness.InvokerKind.OTHER);
        assertThat(observation.all().token()).isEqualTo("UNKNOWN");
        verifyNoProbe(other);

        ClusterInvoker cluster = mock(ClusterInvoker.class);
        Directory unknown = mock(Directory.class);
        forbidProbe(cluster);
        doThrow(new AssertionError("unknown directory must not be probed")).when(unknown).isAvailable();
        when(cluster.getDirectory()).thenReturn(unknown);
        observation = inspect(diagnosticManager(cluster));
        assertThat(observation.state()).isEqualTo(State.UNSUPPORTED);
        assertThat(observation.directoryKind()).isEqualTo(DubboDiscoveryReadiness.DirectoryKind.OTHER);
        verifyNoInteractions(unknown);
        verifyNoProbe(cluster);

        when(cluster.getDirectory()).thenReturn(null);
        observation = inspect(diagnosticManager(cluster));
        assertThat(observation.state()).isEqualTo(State.INVOKER_PRESENT);
        assertThat(observation.directoryKind()).isEqualTo(DubboDiscoveryReadiness.DirectoryKind.MISSING);
        when(cluster.getDirectory()).thenThrow(new IllegalStateException("credential-canary"));
        assertThat(inspect(diagnosticManager(cluster)).state()).isEqualTo(State.INSPECTION_ERROR);
        verifyNoProbe(cluster);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void knownDirectoriesExposeOnlyNonAtomicBoundedCountsWithoutProbingChildren() {
        ClusterInvoker[] invokers = {mock(ClusterInvoker.class), mock(MigrationInvoker.class),
                mock(ServiceDiscoveryMigrationInvoker.class)};
        var kinds = new DubboDiscoveryReadiness.InvokerKind[] {DubboDiscoveryReadiness.InvokerKind.CLUSTER,
                DubboDiscoveryReadiness.InvokerKind.MIGRATION,
                DubboDiscoveryReadiness.InvokerKind.SERVICE_DISCOVERY_MIGRATION};
        for (int i = 0; i < invokers.length; i++) {
            ClusterInvoker invoker = invokers[i];
            DynamicDirectory directory = i == 2 ? mock(ServiceDiscoveryRegistryDirectory.class) : mock(DynamicDirectory.class);
            Invoker child = mock(Invoker.class);
            forbidProbe(invoker);
            forbidProbe(child);
            doThrow(new AssertionError("directory availability is mutating")).when(directory).isAvailable();
            doThrow(new AssertionError("directory URL must remain private")).when(directory).getUrl();
            doThrow(new AssertionError("must not route invocations")).when(directory).list(any());
            when(invoker.getDirectory()).thenReturn(directory);
            when(directory.getAllInvokers()).thenReturn(List.of(child));
            var valid = new BitList(List.of());
            when(directory.getValidInvokers()).thenReturn(valid);
            when(directory.getInvokersToReconnect()).thenReturn(List.of(child));
            when(directory.getDisabledInvokers()).thenReturn(Set.of(child));
            var observation = inspect(diagnosticManager(invoker));
            assertThat(observation.state()).isEqualTo(State.INVOKER_PRESENT);
            assertThat(observation.invokerKind()).isEqualTo(kinds[i]);
            assertThat(observation.directoryKind()).isEqualTo(i == 2
                    ? DubboDiscoveryReadiness.DirectoryKind.SERVICE_DISCOVERY : DubboDiscoveryReadiness.DirectoryKind.DYNAMIC);
            assertThat(observation.destroyed()).isEqualTo(DubboDiscoveryReadiness.Destroyed.FALSE);
            assertThat(observation.all().token()).isEqualTo("1");
            assertThat(observation.valid().token()).isEqualTo("0");
            assertThat(observation.reconnect().token()).isEqualTo("1");
            assertThat(observation.disabled().token()).isEqualTo("1");
            when(directory.getAllInvokers()).thenReturn(List.of());
            when(directory.isDestroyed()).thenReturn(true);
            assertThat(inspect(diagnosticManager(invoker)).all().token()).isEqualTo("0");
            assertThat(inspect(diagnosticManager(invoker)).destroyed()).isEqualTo(DubboDiscoveryReadiness.Destroyed.TRUE);
            verifyNoProbe(invoker);
            verifyNoProbe(child);
            verify(directory, never()).isAvailable();
            verify(directory, never()).getUrl();
            verify(directory, never()).list(any());
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void countFailuresNullsAndOverflowAreNotReportedAsZero() {
        ClusterInvoker invoker = mock(ClusterInvoker.class);
        DynamicDirectory directory = mock(DynamicDirectory.class);
        forbidProbe(invoker);
        doThrow(new AssertionError("must not probe")).when(directory).isAvailable();
        when(invoker.getDirectory()).thenReturn(directory);
        List oversized = mock(List.class);
        when(oversized.size()).thenReturn(10_001);
        when(directory.getAllInvokers()).thenReturn(oversized);
        when(directory.getValidInvokers()).thenReturn(null);
        when(directory.getInvokersToReconnect()).thenThrow(new IllegalStateException("credential-canary"));
        when(directory.getDisabledInvokers()).thenReturn(Set.of());
        when(directory.isDestroyed()).thenThrow(new IllegalStateException("credential-canary"));
        var observation = inspect(diagnosticManager(invoker));
        assertThat(observation.all().token()).isEqualTo("OVERFLOW");
        assertThat(observation.valid().token()).isEqualTo("UNKNOWN");
        assertThat(observation.reconnect().token()).isEqualTo("ERROR");
        assertThat(observation.disabled().token()).isEqualTo("0");
        assertThat(observation.destroyed()).isEqualTo(DubboDiscoveryReadiness.Destroyed.ERROR);
        when(oversized.size()).thenReturn(10_000);
        assertThat(inspect(diagnosticManager(invoker)).all().token()).isEqualTo("10000");
        when(oversized.size()).thenReturn(-1);
        assertThat(inspect(diagnosticManager(invoker)).all().token()).isEqualTo("ERROR");
        verify(oversized, never()).iterator();
        verify(directory, never()).isAvailable();
        verify(directory, never()).getUrl();
        verify(directory, never()).list(any());
        verifyNoProbe(invoker);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void diagnosticInspectionFailureDoesNotChangeSuccessfulGateDecision(CapturedOutput output) throws Exception {
        var manager = mock(ReferenceBeanManager.class);
        var problem = reference(ProblemFactsPort.class, true);
        var users = reference(SubmissionUserReadPort.class, true);
        when(manager.getReferences()).thenReturn(List.of(problem, users))
                .thenThrow(new IllegalStateException("credential-canary"));
        gate(manager).await(Duration.ZERO);
        assertThat(output.getAll()).contains("SubmissionDiscoveryObservation outcome=ready elapsed_ms=")
                .contains("interface=problem_facts state=INSPECTION_ERROR")
                .contains("interface=user_read state=INSPECTION_ERROR")
                .doesNotContain("credential-canary");
    }
}
