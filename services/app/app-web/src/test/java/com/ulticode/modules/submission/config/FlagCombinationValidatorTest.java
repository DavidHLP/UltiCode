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
        return validator;
    }

    private FeatureFlagsProperties flags(boolean usePort, boolean useJudgeOutbox,
                                         boolean useGenerationFence,
                                         int envelopeVersion, LocalDateTime cutoverAt) {
        FeatureFlagsProperties f = new FeatureFlagsProperties();
        f.setUseJudgeOutbox(useJudgeOutbox);
        f.setUseGenerationFence(useGenerationFence);
        f.getJudgeQueue().setUsePort(usePort);
        f.getJudgeQueue().setEnvelopeVersion(envelopeVersion);
        f.getJudgeQueue().setCutoverAt(cutoverAt);
        return f;
    }

    @Test
    @DisplayName("all-off (CI features-off profile) passes")
    void allOffPasses() {
        FeatureFlagsProperties f = flags(false, false, false, 1, null);
        assertThatCode(() -> validator(f).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("F1: use-port=true + use-judge-outbox=false throws (pending orphan risk)")
    void f1ThrowsOnPortWithoutOutbox() {
        FeatureFlagsProperties f = flags(true, false, true, 2, null);
        assertThatThrownBy(() -> validator(f).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("use-port=true")
                .hasMessageContaining("use-judge-outbox=true");
    }

    @Test
    @DisplayName("F1: use-port=true + use-judge-outbox=true passes (the legal cutover combo)")
    void f1PassesWhenBothOn() {
        FeatureFlagsProperties f = flags(true, true, true, 2, null);
        assertThatCode(() -> validator(f).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("F2: use-port=true + generation-fence=false throws")
    void f2ThrowsWithoutGenerationFence() {
        FeatureFlagsProperties f = flags(true, true, false, 2, null);
        assertThatThrownBy(() -> validator(f).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("use-generation-fence=true");
    }

    @Test
    @DisplayName("W1: use-port=true + envelope-version=1 does NOT throw (soft warn, dispatcher hard-codes v2)")
    void w1SoftWarnDoesNotThrow() {
        FeatureFlagsProperties f = flags(true, true, true, 1, null);
        assertThatCode(() -> validator(f).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("W2: use-port=true + past cutover-at does NOT throw (soft warn, stale config)")
    void w2SoftWarnDoesNotThrow() {
        FeatureFlagsProperties f = flags(true, true, true, 2, LocalDateTime.now().minusDays(1));
        assertThatCode(() -> validator(f).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("W2: use-port=true + future cutover-at passes cleanly")
    void w2FutureCutoverPasses() {
        FeatureFlagsProperties f = flags(true, true, true, 2, LocalDateTime.now().plusDays(1));
        assertThatCode(() -> validator(f).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("F3: unrecognized app.runtime.role fails fast (worker/reaper silently unregistered)")
    void f3ThrowsOnUnknownRole() {
        FeatureFlagsProperties f = flags(true, true, true, 2, null);
        FlagCombinationValidator v = validator(f);
        ReflectionTestUtils.setField(v, "runtimeRole", "judge-typo");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.runtime.role")
                .hasMessageContaining("judge-typo");
    }

    @Test
    @DisplayName("F3: api and judge roles both pass")
    void f3AcceptsApiAndJudgeRoles() {
        FeatureFlagsProperties f = flags(true, true, true, 2, null);
        FlagCombinationValidator api = validator(f);
        assertThatCode(api::validate).doesNotThrowAnyException();
        FlagCombinationValidator judge = validator(f);
        ReflectionTestUtils.setField(judge, "runtimeRole", "judge");
        assertThatCode(judge::validate).doesNotThrowAnyException();
    }
}
