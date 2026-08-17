package com.ulticode.modules.submission.reaper;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JudgingLeaseReaperTest {

    @Test
    void recoversExpiredLeaseThroughSubmissionOutbox() {
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        JudgeOutboxMapper judgeOutboxMapper = mock(JudgeOutboxMapper.class);
        FeatureFlagsProperties flags = new FeatureFlagsProperties();
        flags.setUseGenerationFence(true);
        flags.setUseJudgeOutbox(true);
        flags.getJudgeQueue().setUsePort(true);

        Submission submission = new Submission();
        submission.setId("submission-1");
        submission.setProblemId(42L);
        submission.setUserId("user-1");
        submission.setLanguage("java");
        submission.setCode("class Main {}");
        submission.setGeneration(7L);
        when(submissionMapper.selectExpiredJudgingForUpdate(20))
                .thenReturn(List.of(submission));
        when(submissionMapper.bumpGenerationAndReset("submission-1", 7L, 8L))
                .thenReturn(1);

        UuidGenerator uuidGenerator = () -> "outbox-1";
        ObjectProvider<PlatformTransactionManager> noTransactionManager =
                new ObjectProvider<>() {
                    @Override
                    public PlatformTransactionManager getObject() {
                        return null;
                    }

                    @Override
                    public PlatformTransactionManager getObject(Object... args) {
                        return null;
                    }

                    @Override
                    public PlatformTransactionManager getIfAvailable() {
                        return null;
                    }

                    @Override
                    public PlatformTransactionManager getIfUnique() {
                        return null;
                    }
                };

        JudgingLeaseReaper reaper = new JudgingLeaseReaper(
                submissionMapper,
                judgeOutboxMapper,
                flags,
                uuidGenerator,
                new SimpleMeterRegistry(),
                noTransactionManager);

        assertThat(reaper.recoverExpiredLeases()).isEqualTo(1);

        verify(submissionMapper).bumpGenerationAndReset("submission-1", 7L, 8L);
        var record = org.mockito.ArgumentCaptor.forClass(JudgeOutboxRecord.class);
        verify(judgeOutboxMapper).insert(record.capture());
        assertThat(record.getValue().getGeneration()).isEqualTo(8L);
        assertThat(record.getValue().getIsShadow()).isFalse();
        assertThat(record.getValue().getId()).isEqualTo("outbox-1");
    }
}
