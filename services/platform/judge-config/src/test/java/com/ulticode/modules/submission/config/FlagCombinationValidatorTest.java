package com.ulticode.modules.submission.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagCombinationValidatorTest {

    @Test
    void devLiteRequiresStreams() {
        FlagCombinationValidator validator = validator(false, false, false, "dev-lite");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("use-judge-outbox");
    }

    @Test
    void devLiteAcceptsStreams() {
        FlagCombinationValidator validator = validator(true, true, true, "dev-lite");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void legacyRollbackAcceptsLegacyFlags() {
        FlagCombinationValidator validator = validator(false, false, false, "legacy-rollback");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void legacyRollbackRejectsStreamsFlags() {
        FlagCombinationValidator validator = validator(true, true, true, "legacy-rollback");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("legacy-rollback");
    }

    @Test
    void unknownModeFailsClosed() {
        FlagCombinationValidator validator = validator(false, false, false, "typo");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.runtime.mode");
    }

    private static FlagCombinationValidator validator(
            boolean usePort, boolean useOutbox, boolean useFence, String mode) {
        FeatureFlagsProperties flags = new FeatureFlagsProperties();
        flags.setUseJudgeOutbox(useOutbox);
        flags.setUseGenerationFence(useFence);
        flags.getJudgeQueue().setUsePort(usePort);
        FlagCombinationValidator validator = new FlagCombinationValidator(flags);
        set(validator, "runtimeRole", "api");
        set(validator, "runtimeMode", mode);
        return validator;
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to set test runtime field " + name, exception);
        }
    }
}
