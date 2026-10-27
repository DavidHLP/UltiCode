package com.ulticode.submission.config;

import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import java.time.Duration;
import java.util.List;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.reference.ReferenceBeanManager;
import org.apache.dubbo.rpc.Invoker;
import org.junit.jupiter.api.Test;
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
}
