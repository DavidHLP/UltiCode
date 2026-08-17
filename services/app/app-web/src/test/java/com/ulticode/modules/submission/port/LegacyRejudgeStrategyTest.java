package com.ulticode.modules.submission.port;

import com.ulticode.app.api.service.JudgeEnqueuePort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyRejudgeStrategyTest {

    @Mock
    private SubmissionMapper submissionMapper;

    @Mock
    private JudgeEnqueuePort judgeEnqueuePort;

    @Test
    void advancesDefaultGenerationAtomicallyBeforeEnqueueing() {
        Submission submission = submission("submission-1");
        submission.setRetryCount(2);
        when(submissionMapper.bumpGenerationAndReset("submission-1", 1L, 2L)).thenReturn(1);

        var result = new LegacyRejudgeStrategy(submissionMapper, judgeEnqueuePort)
                .rejudge(submission, new com.ulticode.submission.api.dto.RejudgeResult());

        assertThat(submission.getGeneration()).isEqualTo(2L);
        assertThat(submission.getRetryCount()).isEqualTo(3);
        assertThat(result.getSuccess()).isTrue();

        InOrder order = inOrder(submissionMapper, judgeEnqueuePort);
        order.verify(submissionMapper).bumpGenerationAndReset("submission-1", 1L, 2L);
        order.verify(submissionMapper).bumpRetryCount("submission-1", 1);
        order.verify(judgeEnqueuePort).enqueueJudgeJob(
                "submission-1", "42", "user-1", "java", "class Main {}");
    }

    @Test
    void defersEnqueueUntilTransactionCommit() {
        Submission submission = submission("submission-commit");
        when(submissionMapper.bumpGenerationAndReset("submission-commit", 1L, 2L)).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();
        try {
            var result = new LegacyRejudgeStrategy(submissionMapper, judgeEnqueuePort)
                    .rejudge(submission, new com.ulticode.submission.api.dto.RejudgeResult());

            assertThat(result.getSuccess()).isTrue();
            verify(judgeEnqueuePort, org.mockito.Mockito.never())
                    .enqueueJudgeJob("submission-commit", "42", "user-1", "java", "class Main {}");
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(judgeEnqueuePort).enqueueJudgeJob(
                    "submission-commit", "42", "user-1", "java", "class Main {}");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void retriesGenerationCasAfterConcurrentChange() {
        Submission submission = submission("submission-2");
        submission.setGeneration(7L);
        Submission refreshed = submission("submission-2");
        refreshed.setGeneration(8L);
        refreshed.setRetryCount(3);
        when(submissionMapper.bumpGenerationAndReset("submission-2", 7L, 8L)).thenReturn(0);
        when(submissionMapper.selectById("submission-2")).thenReturn(refreshed);
        when(submissionMapper.bumpGenerationAndReset("submission-2", 8L, 9L)).thenReturn(1);

        var result = new LegacyRejudgeStrategy(submissionMapper, judgeEnqueuePort)
                .rejudge(submission, new com.ulticode.submission.api.dto.RejudgeResult());

        assertThat(refreshed.getGeneration()).isEqualTo(9L);
        assertThat(refreshed.getRetryCount()).isEqualTo(4);
        assertThat(result.getSuccess()).isTrue();
        verify(judgeEnqueuePort).enqueueJudgeJob(
                "submission-2", "42", "user-1", "java", "class Main {}");
    }

    private static Submission submission(String id) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setProblemId(42L);
        submission.setUserId("user-1");
        submission.setLanguage("java");
        submission.setCode("class Main {}");
        submission.setStatus("Accepted");
        return submission;
    }
}
