package com.ulticode.judge;

import com.ulticode.modules.queue.migration.JudgeStreamLegacyMigration;
import com.ulticode.modules.queue.outbox.reaper.UnackedStreamEntriesReaper;
import com.ulticode.modules.queue.processor.DefaultJudgeAttemptExecutor;
import com.ulticode.modules.queue.processor.JudgeWorkerProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeRuntimeConfigurationTest {

    @Test
    void importsJudgeOnlyWiringExplicitly() {
        Import imports = JudgeRuntimeConfiguration.class.getAnnotation(Import.class);

        assertThat(imports.value()).contains(
                DefaultJudgeAttemptExecutor.class,
                JudgeWorkerProcessor.class,
                UnackedStreamEntriesReaper.class,
                JudgeStreamLegacyMigration.class);
    }

    @Test
    void judgeOnlyWiringIsNotComponentScannedFromTheSharedArtifact() {
        assertThat(JudgeWorkerProcessor.class.isAnnotationPresent(Component.class)).isFalse();
        assertThat(DefaultJudgeAttemptExecutor.class.isAnnotationPresent(Component.class)).isFalse();
        assertThat(UnackedStreamEntriesReaper.class.isAnnotationPresent(Component.class)).isFalse();
        assertThat(JudgeStreamLegacyMigration.class.isAnnotationPresent(Component.class)).isFalse();
    }
}
