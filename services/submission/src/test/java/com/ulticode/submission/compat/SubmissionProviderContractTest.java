package com.ulticode.submission.compat;

import com.ulticode.app.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.app.api.service.SubmissionFencePort;
import com.ulticode.app.api.service.SubmissionWritePort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Submission compatibility provider contract")
class SubmissionProviderContractTest {

    @Test
    @DisplayName("publishes both app-api ports under the backend-submission group")
    void publishesOwnerPorts() {
        assertProvider(SubmissionWriteCompatibilityProvider.class, SubmissionWritePort.class);
        assertProvider(SubmissionFenceCompatibilityProvider.class, SubmissionFencePort.class);
    }

    @Test
    @DisplayName("forwards every write call to the App writer untouched")
    void writeProviderForwardsToAppWriter() throws Exception {
        SubmissionWriteCompatibilityProvider provider = new SubmissionWriteCompatibilityProvider();
        SubmissionWritePort appWriter = mock(SubmissionWritePort.class);
        inject(provider, appWriter);

        CreateSubmissionDTO request = new CreateSubmissionDTO();
        SubmissionVO expected = new SubmissionVO();
        when(appWriter.submit("user-1", request)).thenReturn(expected);
        when(appWriter.updateSubmissionResultFenced(
                "sub-1", SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 3L, "attempt-1"))
                .thenReturn(true);

        assertThat(provider.submit("user-1", request)).isSameAs(expected);
        provider.updateSubmissionResult("sub-1", SubmissionStatus.WRONG_ANSWER, 8, 2.0, "[]");
        assertThat(provider.updateSubmissionResultFenced(
                "sub-1", SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 3L, "attempt-1"))
                .isTrue();

        verify(appWriter).submit("user-1", request);
        verify(appWriter).updateSubmissionResult("sub-1", SubmissionStatus.WRONG_ANSWER, 8, 2.0, "[]");
        verify(appWriter).updateSubmissionResultFenced(
                "sub-1", SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 3L, "attempt-1");
    }

    @Test
    @DisplayName("forwards every fence call to the App fence untouched")
    void fenceProviderForwardsToAppFence() throws Exception {
        SubmissionFenceCompatibilityProvider provider = new SubmissionFenceCompatibilityProvider();
        SubmissionFencePort appFence = mock(SubmissionFencePort.class);
        inject(provider, appFence);

        when(appFence.currentGeneration("sub-1")).thenReturn(Optional.of(3L));
        when(appFence.acquireLease("sub-1", "attempt-1", 3L, 300L)).thenReturn(true);
        when(appFence.renewLease("sub-1", "attempt-1", 300L)).thenReturn(false);

        assertThat(provider.currentGeneration("sub-1")).contains(3L);
        assertThat(provider.acquireLease("sub-1", "attempt-1", 3L, 300L)).isTrue();
        assertThat(provider.renewLease("sub-1", "attempt-1", 300L)).isFalse();

        verify(appFence).currentGeneration("sub-1");
        verify(appFence).acquireLease("sub-1", "attempt-1", 3L, 300L);
        verify(appFence).renewLease("sub-1", "attempt-1", 300L);
    }

    @Test
    @DisplayName("delegates to the backend-app group with no retries")
    void referencesAppGroupWithoutRetries() throws Exception {
        assertThat(reference(SubmissionWriteCompatibilityProvider.class, "appWriter"))
                .extracting(r -> r.group(), r -> r.version(), r -> r.retries(), r -> r.check())
                .containsExactly("backend-app", "1.0.0", 0, false);
        assertThat(reference(SubmissionFenceCompatibilityProvider.class, "appFence"))
                .extracting(r -> r.group(), r -> r.version(), r -> r.retries(), r -> r.check())
                .containsExactly("backend-app", "1.0.0", 0, false);
    }

    private void inject(Object provider, Object delegate) throws Exception {
        for (Field field : provider.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(DubboReference.class)) {
                field.setAccessible(true);
                field.set(provider, delegate);
            }
        }
    }

    private DubboReference reference(Class<?> provider, String fieldName) throws Exception {
        Field field = provider.getDeclaredField(fieldName);
        return field.getAnnotation(DubboReference.class);
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
