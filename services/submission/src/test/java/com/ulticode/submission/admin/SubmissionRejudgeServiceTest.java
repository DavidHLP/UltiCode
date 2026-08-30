package com.ulticode.submission.admin;

import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxMapper;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.submission.api.dto.RejudgeResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionRejudgeServiceTest {

    @Mock private SubmissionMapper submissionMapper;
    @Mock private SubmissionCreatedOutboxMapper createdOutboxMapper;
    @Mock private JudgeOutboxMapper judgeOutboxMapper;
    @Mock private UuidGenerator uuidGenerator;

    private SubmissionRejudgeService service;

    @BeforeEach
    void setUp() {
        service = new SubmissionRejudgeService(
                submissionMapper,
                createdOutboxMapper,
                judgeOutboxMapper,
                uuidGenerator,
                Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void missingSubmissionFailsWithoutSideEffects() {
        RejudgeResultDTO result = service.rejudge("missing");

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo(AppErrorCode.CONTENT_NOT_FOUND.code());
        verify(judgeOutboxMapper, never()).insert(any(JudgeOutboxRecord.class));
    }

    @Test
    void contestSubmissionFailsWithoutSideEffects() {
        when(submissionMapper.selectById("sub-1")).thenReturn(submission("Accepted", 3L, 0));
        when(createdOutboxMapper.findLatestBySubmissionId("sub-1"))
                .thenReturn(new com.ulticode.modules.submission.created.SubmissionCreatedOutboxRecord());

        RejudgeResultDTO result = service.rejudge("sub-1");

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo(AppErrorCode.CONTENT_STATE_CONFLICT.code());
        verify(judgeOutboxMapper, never()).insert(any(JudgeOutboxRecord.class));
    }

    @Test
    void terminalSubmissionBumpsGenerationAndWritesOneRealJudgeTask() {
        Submission row = submission("Accepted", 3L, 2);
        when(submissionMapper.selectById("sub-1")).thenReturn(row);
        when(submissionMapper.rejudgeTerminal("sub-1", 3L, 4L)).thenReturn(1);
        when(uuidGenerator.newId()).thenReturn("outbox-1");

        RejudgeResultDTO result = service.rejudge("sub-1");

        assertThat(result.success()).isTrue();
        assertThat(result.newStatus()).isEqualTo("Pending");
        assertThat(result.retryCount()).isEqualTo(3);
        ArgumentCaptor<JudgeOutboxRecord> task = ArgumentCaptor.forClass(JudgeOutboxRecord.class);
        verify(judgeOutboxMapper).insert(task.capture());
        assertThat(task.getValue().getGeneration()).isEqualTo(4L);
        assertThat(task.getValue().getIsShadow()).isFalse();
    }

    @Test
    void concurrentTerminalChangeFailsWithoutJudgeTask() {
        when(submissionMapper.selectById("sub-1")).thenReturn(submission("Wrong Answer", 3L, 0));
        when(submissionMapper.rejudgeTerminal("sub-1", 3L, 4L)).thenReturn(0);

        RejudgeResultDTO result = service.rejudge("sub-1");

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo(AppErrorCode.VERSION_CONFLICT.code());
        verify(judgeOutboxMapper, never()).insert(any(JudgeOutboxRecord.class));
    }

    @Test
    void judgingRequestExpiresCurrentLeaseWithoutDuplicateTask() {
        Submission row = submission("Judging", 5L, 1);
        Submission updated = submission("Judging", 5L, 2);
        when(submissionMapper.selectById("sub-1")).thenReturn(row, updated);
        when(submissionMapper.requestJudgingRejudge("sub-1", 5L)).thenReturn(1);

        RejudgeResultDTO result = service.rejudge("sub-1");

        assertThat(result.success()).isTrue();
        assertThat(result.newStatus()).isEqualTo("Judging");
        assertThat(result.retryCount()).isEqualTo(2);
        verify(judgeOutboxMapper, never()).insert(any(JudgeOutboxRecord.class));
    }

    @Test
    void pendingSubmissionIsNotRejudgeable() {
        when(submissionMapper.selectById("sub-1")).thenReturn(submission("Pending", 1L, 0));

        RejudgeResultDTO result = service.rejudge("sub-1");

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo(AppErrorCode.CONTENT_STATE_CONFLICT.code());
        verify(judgeOutboxMapper, never()).insert(any(JudgeOutboxRecord.class));
    }

    private static Submission submission(String status, Long generation, int retryCount) {
        Submission submission = new Submission();
        submission.setId("sub-1");
        submission.setUserId("user-1");
        submission.setProblemId(101L);
        submission.setLanguage("java");
        submission.setCode("class Main {}");
        submission.setStatus(status);
        submission.setGeneration(generation);
        submission.setRetryCount(retryCount);
        return submission;
    }
}
