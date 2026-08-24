package com.ulticode.submission.provider;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.port.DefaultSubmissionFencePort;
import com.ulticode.modules.submission.port.DefaultSubmissionWritePort;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.submission.api.service.SubmissionFencePort;
import com.ulticode.submission.api.service.SubmissionWritePort;
import com.ulticode.submission.api.service.SubmissionUserQueryPort;
import com.ulticode.submission.dubbo.provider.SubmissionFenceProvider;
import com.ulticode.submission.dubbo.provider.SubmissionUserQueryProvider;
import com.ulticode.submission.dubbo.provider.SubmissionWriteProvider;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Submission owner provider contract")
class SubmissionProviderContractTest {

    @Test
    @DisplayName("publishes both owner ports under the backend-submission group")
    void publishesOwnerPorts() {
        // Write contract is unchanged since 1.0.0; the fence contract bumped
        // to 1.1.0 for the nullable-Long currentGeneration wire change.
        assertProvider(SubmissionWriteProvider.class, SubmissionWritePort.class, "1.0.0");
        assertProvider(SubmissionFenceProvider.class, SubmissionFencePort.class, "1.1.0");
    }

    @Test
    @DisplayName("publishes the changed user query contract under the 1.1.0 gate")
    void publishesVersionedUserQueryPort() {
        assertProvider(SubmissionUserQueryProvider.class, SubmissionUserQueryPort.class, "1.1.0");
    }

    @Test
    @DisplayName("forwards every write call to the local Submission owner")
    void writeProviderForwardsToLocalWriter() {
        DefaultSubmissionWritePort localWriter = mock(DefaultSubmissionWritePort.class);
        SubmissionWriteProvider provider = new SubmissionWriteProvider(localWriter);
        CreateSubmissionDTO request = new CreateSubmissionDTO();
        SubmissionFactsSnapshot facts = new SubmissionFactsSnapshot(
                "user-1", true,
                new SubmissionFactsSnapshot.ProblemFacts(
                        101L, "Two Sum", "two-sum", 2, 256, null),
                1L, SubmissionFactsSnapshot.CURRENT_SCHEMA_VERSION);
        SubmissionVO expected = new SubmissionVO();
        when(localWriter.submit("user-1", request, facts)).thenReturn(expected);
        when(localWriter.submitContest("user-1", request, facts)).thenReturn(expected);
        when(localWriter.updateSubmissionResultFenced(
                "sub-1", SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 3L, "attempt-1"))
                .thenReturn(true);

        assertThat(provider.submit("user-1", request, facts)).isSameAs(expected);
        assertThat(provider.submitContest("user-1", request, facts)).isSameAs(expected);
        provider.updateSubmissionResult("sub-1", SubmissionStatus.WRONG_ANSWER, 8, 2.0, "[]");
        assertThat(provider.updateSubmissionResultFenced(
                "sub-1", SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 3L, "attempt-1"))
                .isTrue();

        verify(localWriter).submit("user-1", request, facts);
        verify(localWriter).submitContest("user-1", request, facts);
        verify(localWriter).updateSubmissionResult(
                "sub-1", SubmissionStatus.WRONG_ANSWER, 8, 2.0, "[]");
        verify(localWriter).updateSubmissionResultFenced(
                "sub-1", SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 3L, "attempt-1");
    }

    @Test
    @DisplayName("forwards every fence call to the local Submission owner")
    void fenceProviderForwardsToLocalFence() {
        DefaultSubmissionFencePort localFence = mock(DefaultSubmissionFencePort.class);
        SubmissionFenceProvider provider = new SubmissionFenceProvider(localFence);
        when(localFence.currentGeneration("sub-1")).thenReturn(3L);
        when(localFence.acquireLease("sub-1", "attempt-1", 3L, 300L)).thenReturn(true);
        when(localFence.renewLease("sub-1", "attempt-1", 300L)).thenReturn(false);

        assertThat(provider.currentGeneration("sub-1")).isEqualTo(3L);
        assertThat(provider.acquireLease("sub-1", "attempt-1", 3L, 300L)).isTrue();
        assertThat(provider.renewLease("sub-1", "attempt-1", 300L)).isFalse();

        verify(localFence).currentGeneration("sub-1");
        verify(localFence).acquireLease("sub-1", "attempt-1", 3L, 300L);
        verify(localFence).renewLease("sub-1", "attempt-1", 300L);
    }

    @Test
    @DisplayName("has no App compatibility reference or owner-mode selector")
    void providerHasNoCompatibilityReference() {
        assertNoDubboReference(SubmissionWriteProvider.class);
        assertNoDubboReference(SubmissionFenceProvider.class);
        assertNoOwnerModeSelector(SubmissionWriteProvider.class);
        assertNoOwnerModeSelector(SubmissionFenceProvider.class);
    }

    @Test
    @DisplayName("propagates provider timeout RpcException without masking")
    void propagatesProviderTimeout() {
        DefaultSubmissionWritePort localWriter = mock(DefaultSubmissionWritePort.class);
        when(localWriter.submit(any(), any(), any()))
                .thenThrow(new org.apache.dubbo.rpc.RpcException(
                        org.apache.dubbo.rpc.RpcException.TIMEOUT_EXCEPTION, "Dubbo RPC timeout"));

        SubmissionWriteProvider provider = new SubmissionWriteProvider(localWriter);
        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        SubmissionFactsSnapshot facts = new SubmissionFactsSnapshot("u-1", true, null, 1L, 1);

        assertThatThrownBy(() -> provider.submit("u-1", dto, facts))
                .isInstanceOf(org.apache.dubbo.rpc.RpcException.class)
                .matches(e -> ((org.apache.dubbo.rpc.RpcException) e).isTimeout())
                .hasMessageContaining("Dubbo RPC timeout");
    }

    @Test
    @DisplayName("propagates network partition RpcException without masking")
    void propagatesNetworkPartitionException() {
        DefaultSubmissionWritePort localWriter = mock(DefaultSubmissionWritePort.class);
        when(localWriter.submit(any(), any(), any()))
                .thenThrow(new org.apache.dubbo.rpc.RpcException(
                        org.apache.dubbo.rpc.RpcException.NETWORK_EXCEPTION, "Network partition / connection refused"));

        SubmissionWriteProvider provider = new SubmissionWriteProvider(localWriter);
        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        SubmissionFactsSnapshot facts = new SubmissionFactsSnapshot("u-1", true, null, 1L, 1);

        assertThatThrownBy(() -> provider.submit("u-1", dto, facts))
                .isInstanceOf(org.apache.dubbo.rpc.RpcException.class)
                .matches(e -> ((org.apache.dubbo.rpc.RpcException) e).isNetwork())
                .hasMessageContaining("Network partition / connection refused");
    }

    private void assertNoDubboReference(Class<?> provider) {
        for (Field field : provider.getDeclaredFields()) {
            assertThat(field.isAnnotationPresent(DubboReference.class))
                    .as("field %s must not be a compatibility RPC reference", field.getName())
                    .isFalse();
        }
    }

    private void assertNoOwnerModeSelector(Class<?> provider) {
        assertThat(provider.getAnnotation(ConditionalOnProperty.class))
                .as("provider must not be conditional on an owner-mode property")
                .isNull();
        for (Field field : provider.getDeclaredFields()) {
            assertThat(field.isAnnotationPresent(Value.class))
                    .as("field %s must not bind an owner-mode property", field.getName())
                    .isFalse();
            assertThat(field.getName())
                    .as("field %s must not retain an owner-mode selector", field.getName())
                    .doesNotContain("ownerMode");
        }
    }

    private void assertProvider(Class<?> provider, Class<?> contract, String version) {
        assertThat(provider.getInterfaces()).contains(contract);
        DubboService service = provider.getAnnotation(DubboService.class);
        assertThat(service).isNotNull();
        assertThat(service.group()).isEqualTo("backend-submission");
        assertThat(service.version()).isEqualTo(version);
        if (provider.getAnnotation(Profile.class) != null) {
            assertThat(provider.getAnnotation(Profile.class).value()).contains("!test");
        }
    }
}
