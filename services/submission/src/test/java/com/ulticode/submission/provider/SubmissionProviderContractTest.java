package com.ulticode.submission.provider;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.port.DefaultSubmissionFencePort;
import com.ulticode.modules.submission.port.DefaultSubmissionWritePort;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionFencePort;
import com.ulticode.submission.api.service.SubmissionWritePort;
import com.ulticode.submission.dubbo.provider.SubmissionFenceProvider;
import com.ulticode.submission.dubbo.provider.SubmissionWriteProvider;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Submission owner provider contract")
class SubmissionProviderContractTest {

    @Test
    @DisplayName("publishes both owner ports under the backend-submission group")
    void publishesOwnerPorts() {
        assertProvider(SubmissionWriteProvider.class, SubmissionWritePort.class);
        assertProvider(SubmissionFenceProvider.class, SubmissionFencePort.class);
    }

    @Test
    @DisplayName("forwards every write call to the local Submission owner")
    void writeProviderForwardsToLocalWriter() {
        DefaultSubmissionWritePort localWriter = mock(DefaultSubmissionWritePort.class);
        SubmissionWriteProvider provider = new SubmissionWriteProvider(localWriter);
        CreateSubmissionDTO request = new CreateSubmissionDTO();
        SubmissionVO expected = new SubmissionVO();
        when(localWriter.submit("user-1", request)).thenReturn(expected);
        when(localWriter.submitContest("user-1", request)).thenReturn(expected);
        when(localWriter.updateSubmissionResultFenced(
                "sub-1", SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 3L, "attempt-1"))
                .thenReturn(true);

        assertThat(provider.submit("user-1", request)).isSameAs(expected);
        assertThat(provider.submitContest("user-1", request)).isSameAs(expected);
        provider.updateSubmissionResult("sub-1", SubmissionStatus.WRONG_ANSWER, 8, 2.0, "[]");
        assertThat(provider.updateSubmissionResultFenced(
                "sub-1", SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 3L, "attempt-1"))
                .isTrue();

        verify(localWriter).submit("user-1", request);
        verify(localWriter).submitContest("user-1", request);
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
        when(localFence.currentGeneration("sub-1")).thenReturn(Optional.of(3L));
        when(localFence.acquireLease("sub-1", "attempt-1", 3L, 300L)).thenReturn(true);
        when(localFence.renewLease("sub-1", "attempt-1", 300L)).thenReturn(false);

        assertThat(provider.currentGeneration("sub-1")).contains(3L);
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
    }

    private void assertNoDubboReference(Class<?> provider) {
        for (Field field : provider.getDeclaredFields()) {
            assertThat(field.isAnnotationPresent(DubboReference.class))
                    .as("field %s must not be a compatibility RPC reference", field.getName())
                    .isFalse();
        }
    }

    private void assertProvider(Class<?> provider, Class<?> contract) {
        assertThat(provider.getInterfaces()).contains(contract);
        DubboService service = provider.getAnnotation(DubboService.class);
        assertThat(service).isNotNull();
        assertThat(service.group()).isEqualTo("backend-submission");
        assertThat(service.version()).isEqualTo("1.0.0");
        assertThat(provider.getAnnotation(Profile.class).value()).contains("!test");
    }
}
