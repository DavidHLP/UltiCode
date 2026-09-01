package com.ulticode.modules.submission.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1-1: {@link FlagCombinationValidator} rule coverage. Pure unit (no Spring
 * context) — constructs {@link FeatureFlagsProperties} directly and invokes
 * {@code validate()}.
 */
@DisplayName("P1-1 FlagCombinationValidator rules")
class FlagCombinationValidatorTest {

    private FlagCombinationValidator validator(FeatureFlagsProperties flags) {
        FlagCombinationValidator validator = new FlagCombinationValidator(flags);
        // The runtime role is @Value-injected in production; default to the
        // api runtime here so F1/F2/W1/W2 tests are not affected by F3.
        ReflectionTestUtils.setField(validator, "runtimeRole", "api");
        ReflectionTestUtils.setField(validator, "runtimeMode", "dev-lite");
        return validator;
    }

    private FlagCombinationValidator externalFullValidator(FeatureFlagsProperties flags) {
        FlagCombinationValidator validator = validator(flags);
        ReflectionTestUtils.setField(validator, "runtimeMode", "external-full");
        return validator;
    }

    private FeatureFlagsProperties flags(boolean usePort, boolean useJudgeOutbox,
                                         boolean useGenerationFence,
                                         LocalDateTime cutoverAt) {
        FeatureFlagsProperties f = new FeatureFlagsProperties();
        f.setUseJudgeOutbox(useJudgeOutbox);
        f.setUseGenerationFence(useGenerationFence);
        f.getJudgeQueue().setUsePort(usePort);
        f.getJudgeQueue().setCutoverAt(cutoverAt);
        return f;
    }

    @Test
    @DisplayName("F1: use-port=true + use-judge-outbox=false throws (pending orphan risk)")
    void f1ThrowsOnPortWithoutOutbox() {
        FeatureFlagsProperties f = flags(true, false, true, null);
        assertThatThrownBy(() -> validator(f).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("use-port=true")
                .hasMessageContaining("use-judge-outbox=true");
    }

    @Test
    @DisplayName("F1: use-port=true + use-judge-outbox=true passes (the legal cutover combo)")
    void f1PassesWhenBothOn() {
        FeatureFlagsProperties f = flags(true, true, true, null);
        assertThatCode(() -> externalFullValidator(f).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("F2: use-port=true + generation-fence=false throws")
    void f2ThrowsWithoutGenerationFence() {
        FeatureFlagsProperties f = flags(true, true, false, null);
        assertThatThrownBy(() -> validator(f).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("use-generation-fence=true");
    }

    @Test
    @DisplayName("W1: use-port=true + past cutover-at does NOT throw (soft warn, stale config)")
    void w2SoftWarnDoesNotThrow() {
        FeatureFlagsProperties f = flags(true, true, true, LocalDateTime.now().minusDays(1));
        assertThatCode(() -> externalFullValidator(f).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("W2: use-port=true + future cutover-at passes cleanly")
    void w2FutureCutoverPasses() {
        FeatureFlagsProperties f = flags(true, true, true, LocalDateTime.now().plusDays(1));
        assertThatCode(() -> externalFullValidator(f).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("F3: unrecognized app.runtime.role fails fast (worker/reaper silently unregistered)")
    void f3ThrowsOnUnknownRole() {
        FeatureFlagsProperties f = flags(true, true, true, null);
        FlagCombinationValidator v = externalFullValidator(f);
        ReflectionTestUtils.setField(v, "runtimeRole", "judge-typo");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.runtime.role")
                .hasMessageContaining("judge-typo");
    }

    @Test
    @DisplayName("F3: api and judge roles both pass")
    void f3AcceptsApiAndJudgeRoles() {
        FeatureFlagsProperties f = flags(true, true, true, null);
        FlagCombinationValidator api = externalFullValidator(f);
        assertThatCode(api::validate).doesNotThrowAnyException();
        FlagCombinationValidator judge = externalFullValidator(f);
        ReflectionTestUtils.setField(judge, "runtimeRole", "judge");
        assertThatCode(judge::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("named dev-lite requires the Streams path")
    void devLiteRequiresStreams() {
        FeatureFlagsProperties f = flags(false, false, false, null);
        FlagCombinationValidator v = validator(f);
        ReflectionTestUtils.setField(v, "runtimeMode", "dev-lite");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev-lite")
                .hasMessageContaining("use-judge-outbox");
    }

    @Test
    @DisplayName("legacy rollback runtime mode fails closed")
    void legacyRollbackFailsClosed() {
        FeatureFlagsProperties f = flags(false, false, false, null);
        FlagCombinationValidator v = validator(f);
        ReflectionTestUtils.setField(v, "runtimeMode", "legacy-rollback");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.runtime.mode")
                .hasMessageContaining("legacy-rollback");
    }

    @Test
    @DisplayName("named dev-full requires the complete Streams cutover flags")
    void devFullRequiresFullFlags() {
        FeatureFlagsProperties f = flags(false, false, false, null);
        FlagCombinationValidator v = validator(f);
        ReflectionTestUtils.setField(v, "runtimeMode", "dev-full");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev-full")
                .hasMessageContaining("use-judge-outbox");
    }

    @Test
    @DisplayName("unknown named runtime mode fails closed")
    void unknownRuntimeModeFails() {
        FeatureFlagsProperties f = flags(false, false, false, null);
        FlagCombinationValidator v = validator(f);
        ReflectionTestUtils.setField(v, "runtimeMode", "typo");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.runtime.mode")
                .hasMessageContaining("typo");
    }
}
