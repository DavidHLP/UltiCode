package com.ulticode.submission.config;

import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.rpc.resilience.DubboDiscoveryReadiness;
import com.ulticode.rpc.resilience.DubboDiscoveryReadiness.State;
import java.time.Duration;
import java.util.List;
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

    @Test
    void diagnosticStatesAreFixedAndDoNotInvokeBusinessCalls() {
        String type = ProblemFactsPort.class.getName();
        assertThat(DubboDiscoveryReadiness.inspect(null, type)).isEqualTo(State.MANAGER_MISSING);
        var manager = mock(ReferenceBeanManager.class);
        when(manager.getReferences()).thenReturn(List.of());
        assertThat(DubboDiscoveryReadiness.inspect(manager, type)).isEqualTo(State.REFERENCE_MISSING);
        var problem = reference(ProblemFactsPort.class, false);
        var config = problem.getReferenceConfig();
        var invoker = config.getInvoker();
        when(manager.getReferences()).thenReturn(List.of(problem));
        assertThat(DubboDiscoveryReadiness.inspect(manager, type)).isEqualTo(State.UNAVAILABLE);
        when(invoker.isAvailable()).thenReturn(true);
        assertThat(DubboDiscoveryReadiness.inspect(manager, type)).isEqualTo(State.AVAILABLE);
        when(config.getInvoker()).thenReturn(null);
        assertThat(DubboDiscoveryReadiness.inspect(manager, type)).isEqualTo(State.INVOKER_MISSING);
        when(problem.getReferenceConfig()).thenReturn(null);
        assertThat(DubboDiscoveryReadiness.inspect(manager, type)).isEqualTo(State.CONFIG_MISSING);
        when(problem.getReferenceConfig()).thenThrow(new IllegalStateException("credential-canary"));
        assertThat(DubboDiscoveryReadiness.inspect(manager, type)).isEqualTo(State.INSPECTION_ERROR);
        assertThat(gate(manager).ready()).isFalse();
        assertThatThrownBy(() -> gate(manager).await(Duration.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Submission projection providers were not discovered before readiness deadline");
        verify(problem, never()).getObject();
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
                .contains("problem_facts=INSPECTION_ERROR user_read=INSPECTION_ERROR")
                .doesNotContain("credential-canary");
    }
}
