package com.ulticode.modules.submission.port;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.ulticode.app.api.service.CodeExecutionPort;
import com.ulticode.app.api.service.JudgeFeatureFlagsPort;
import com.ulticode.modules.submission.port.VerdictResolvePort;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.port.adapter.RemoteSubmissionWritePort;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.service.VerdictResolver;
import com.ulticode.submission.api.service.SubmissionFencePort;
import com.ulticode.submission.api.service.SubmissionIntakePort;
import com.ulticode.submission.api.service.SubmissionVerdictWritePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("App exposes only the remote Submission-owner intake adapter")
    void appHasNoLocalSubmissionMutationImplementation() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.ulticode.modules.submission");

        List<String> intakeImplementations = classes.stream()
                .filter(javaClass -> !javaClass.isInterface())
                .filter(javaClass -> javaClass.isAssignableTo(SubmissionIntakePort.class))
                .map(javaClass -> javaClass.getName())
                .sorted()
                .toList();
        List<String> localMutationImplementations = classes.stream()
                .filter(javaClass -> !javaClass.isInterface())
                .filter(javaClass -> javaClass.isAssignableTo(SubmissionVerdictWritePort.class)
                        || javaClass.isAssignableTo(SubmissionFencePort.class))
                .map(javaClass -> javaClass.getName())
                .sorted()
                .toList();

        assertThat(intakeImplementations).containsExactly(RemoteSubmissionWritePort.class.getName());
        assertThat(localMutationImplementations).isEmpty();
    }

    @Test
    @DisplayName("VerdictResolver implements the judge-runtime private VerdictResolvePort")
    void verdictResolvePortWiring() {
        assertTrue(VerdictResolvePort.class.isAssignableFrom(VerdictResolver.class),
                "VerdictResolver must implement the judge-runtime private VerdictResolvePort");
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
