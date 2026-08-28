package com.ulticode.modules.submission.port;

import com.ulticode.app.api.service.CodeExecutionPort;
import com.ulticode.app.api.service.JudgeFeatureFlagsPort;
import com.ulticode.submission.api.service.SubmissionFencePort;
import com.ulticode.submission.api.service.SubmissionIntakePort;
import com.ulticode.submission.api.service.SubmissionVerdictWritePort;
import com.ulticode.app.api.service.VerdictResolvePort;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.service.VerdictResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring assertion that verifies the app-api port interfaces are implemented by
 * the backend-app default adapters.
 *
 * <p>This test catches the {@code F-003} bug class (port interface fragmentation)
 * where the consumer injects an app-api port type but the provider implements a
 * different type. The assertion is a compile-time + runtime type-assignment
 * check: if the impl class does not implement the app-api interface, the test
 * fails to compile or the {@code isAssignableFrom} check returns false.
 *
 * @author ulticode
 */
@DisplayName("Submission port wiring: app-api interface ↔ backend-app impl")
class SubmissionPortWiringTest {

    @Test
    @DisplayName("DefaultSubmissionFencePort implements app-api SubmissionFencePort")
    void fencePortWiring() {
        assertTrue(SubmissionFencePort.class.isAssignableFrom(DefaultSubmissionFencePort.class),
                "DefaultSubmissionFencePort must implement com.ulticode.submission.api.service.SubmissionFencePort");
    }

    @Test
    @DisplayName("DefaultSubmissionWritePort implements narrow mutation ports")
    void writePortWiring() {
        assertTrue(SubmissionIntakePort.class.isAssignableFrom(DefaultSubmissionWritePort.class));
        assertTrue(SubmissionVerdictWritePort.class.isAssignableFrom(DefaultSubmissionWritePort.class));
    }

    @Test
    @DisplayName("VerdictResolver implements app-api VerdictResolvePort")
    void verdictResolvePortWiring() {
        assertTrue(VerdictResolvePort.class.isAssignableFrom(VerdictResolver.class),
                "VerdictResolver must implement com.ulticode.app.api.service.VerdictResolvePort");
    }

    @Test
    @DisplayName("CodeExecutionService implements app-api CodeExecutionPort")
    void codeExecutionPortWiring() {
        assertTrue(CodeExecutionPort.class.isAssignableFrom(CodeExecutionService.class),
                "CodeExecutionService must implement com.ulticode.app.api.service.CodeExecutionPort");
    }

    @Test
    @DisplayName("DefaultJudgeFeatureFlagsPort implements app-api JudgeFeatureFlagsPort")
    void judgeFeatureFlagsPortWiring() {
        assertTrue(JudgeFeatureFlagsPort.class.isAssignableFrom(DefaultJudgeFeatureFlagsPort.class),
                "DefaultJudgeFeatureFlagsPort must implement com.ulticode.app.api.service.JudgeFeatureFlagsPort");
    }

    @Test
    @DisplayName("DefaultJudgeFeatureFlagsPort delegates both feature flags")
    void judgeFeatureFlagsPortDelegates() {
        FeatureFlagsProperties properties = new FeatureFlagsProperties();
        properties.setUseGenerationFence(true);
        properties.getJudgeQueue().setUsePort(true);

        DefaultJudgeFeatureFlagsPort adapter = new DefaultJudgeFeatureFlagsPort(properties);

        assertTrue(adapter.isUseGenerationFence());
        assertTrue(adapter.isJudgeQueueUsePort());

        properties.setUseGenerationFence(false);
        properties.getJudgeQueue().setUsePort(false);

        assertFalse(adapter.isUseGenerationFence());
        assertFalse(adapter.isJudgeQueueUsePort());
    }
}
