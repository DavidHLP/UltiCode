package com.ulticode.submission.compat;

import com.ulticode.app.api.service.SubmissionFencePort;
import com.ulticode.app.api.service.SubmissionWritePort;
import org.apache.dubbo.config.annotation.DubboService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Submission compatibility provider contract")
class SubmissionProviderContractTest {

    @Test
    @DisplayName("publishes both app-api ports under the backend-submission group")
    void publishesOwnerPorts() {
        assertProvider(SubmissionWriteCompatibilityProvider.class, SubmissionWritePort.class);
        assertProvider(SubmissionFenceCompatibilityProvider.class, SubmissionFencePort.class);
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
