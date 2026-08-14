package com.ulticode.modules.queue.processor;

import com.ulticode.app.api.service.JudgeFeatureFlagsPort;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.port.JudgeJobEnvelope;
import com.ulticode.modules.queue.port.JudgeJobHandle;
import com.ulticode.modules.queue.port.JudgeQueue;
import com.ulticode.modules.queue.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Judge worker processor")
class JudgeWorkerProcessorTest {

    @Mock
    private QueueService queueService;

    @Mock
    private JudgeFeatureFlagsPort featureFlags;

    @Mock
    private ObjectProvider<JudgeQueue> judgeQueueProvider;

    @Mock
    private JudgeQueue judgeQueue;

    @Mock
    private JudgeAttemptExecutor attemptExecutor;

    private QueueConfig queueConfig;
    private JudgeWorkerProcessor processor;

    @BeforeEach
    void setUp() {
        queueConfig = new QueueConfig();
        queueConfig.setMaxConcurrentJobs(1);
        processor = new JudgeWorkerProcessor(
                queueService, queueConfig, featureFlags, judgeQueueProvider, attemptExecutor);
    }

    @Test
    @DisplayName("legacy poll delegates a queued job to the attempt executor")
    void legacyPollDelegatesJob() {
        JudgeJob job = judgeJob("job-1", "submission-1");
        when(queueService.pollJob(QueueConstants.JUDGE_QUEUE)).thenReturn(job);

        processor.pollAndProcess();

        verify(attemptExecutor).runAttempt(same(job), isNull(), isNull());
    }

    @Test
    @DisplayName("legacy poll ignores an empty or malformed queue result")
    void legacyPollIgnoresNonJudgeJob() {
        when(queueService.pollJob(QueueConstants.JUDGE_QUEUE)).thenReturn("not-a-judge-job");

        processor.pollAndProcess();

        verifyNoInteractions(attemptExecutor);
    }

    @Test
    @DisplayName("legacy poll is disabled while the Streams cutover is active")
    void legacyPollDisabledDuringStreamsCutover() {
        when(featureFlags.isJudgeQueueUsePort()).thenReturn(true);

        processor.pollAndProcess();

        verifyNoInteractions(queueService, attemptExecutor);
    }

    @Test
    @DisplayName("port poll is a no-op when the queue-port flag is disabled")
    void portPollDisabledIsNoOp() {
        when(featureFlags.isJudgeQueueUsePort()).thenReturn(false);

        processor.pollAndProcessFromPort();

        verify(judgeQueueProvider, never()).getIfAvailable();
        verifyNoInteractions(judgeQueue);
    }

    @Test
    @DisplayName("port poll reconstructs the envelope and delegates the leased handle")
    void portPollDelegatesReconstructedJob() {
        when(featureFlags.isJudgeQueueUsePort()).thenReturn(true);
        when(judgeQueueProvider.getIfAvailable()).thenReturn(judgeQueue);
        JudgeJobEnvelope envelope = new JudgeJobEnvelope(
                JudgeJobEnvelope.VERSION_2,
                "job-2",
                "submission-2",
                "100",
                "user-2",
                "java",
                "class Solution {}",
                2000,
                262144,
                7L,
                "attempt-2");
        JudgeJobHandle handle = new JudgeJobHandle(envelope, "ack-token");
        when(judgeQueue.poll(500L)).thenReturn(Optional.of(handle));

        processor.pollAndProcessFromPort();

        ArgumentCaptor<JudgeJob> jobCaptor = ArgumentCaptor.forClass(JudgeJob.class);
        verify(attemptExecutor).runAttempt(jobCaptor.capture(), same(judgeQueue), same(handle));
        JudgeJob reconstructed = jobCaptor.getValue();
        assertThat(reconstructed.getId()).isEqualTo("job-2");
        assertThat(reconstructed.getSubmissionId()).isEqualTo("submission-2");
        assertThat(reconstructed.getProblemId()).isEqualTo("100");
        assertThat(reconstructed.getUserId()).isEqualTo("user-2");
        assertThat(reconstructed.getLanguage()).isEqualTo("java");
        assertThat(reconstructed.getCode()).isEqualTo("class Solution {}");
    }

    @Test
    @DisplayName("process reports completed after delegating the job")
    void processReturnsCompleted() throws Exception {
        JudgeJob job = judgeJob("job-3", "submission-3");

        var status = processor.process(job);

        assertThat(status.getJobId()).isEqualTo("job-3");
        assertThat(status.getJobType()).isEqualTo(QueueConstants.JUDGE_QUEUE);
        assertThat(status.getStatus()).isEqualTo(QueueConstants.JobStatus.COMPLETED);
        verify(attemptExecutor).runAttempt(same(job), isNull(), isNull());
    }

    private JudgeJob judgeJob(String id, String submissionId) {
        JudgeJob job = new JudgeJob();
        job.setId(id);
        job.setSubmissionId(submissionId);
        job.setProblemId("100");
        job.setUserId("user-1");
        job.setLanguage("java");
        job.setCode("class Solution {}");
        return job;
    }
}
