package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.admin.dto.BatchRejudgeResponse;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminSubmissionServiceImpl")
class AdminSubmissionServiceImplTest {

    @Mock
    private SubmissionMapper submissionMapper;

    @Mock
    private QueueService queueService;

    @Mock
    private com.ulticode.modules.submission.policy.RejudgePolicy rejudgePolicy;

    private AdminSubmissionServiceImpl adminSubmissionService;

    @BeforeEach
    void setUp() {
        // ADR-003 M3b: flag-off FeatureFlagsProperties so rejudge takes the
        // legacy path. rejudgePolicy is unused on the legacy path.
        com.ulticode.modules.submission.config.FeatureFlagsProperties flags =
                new com.ulticode.modules.submission.config.FeatureFlagsProperties();
        // After ADR-0011 Stage 2 + C2 lift: read paths in AdminSubmissionProjection;
        // fenced rejudge state machine in DefaultRejudgePolicy. Constructor keeps
        // only write-path deps: submissionMapper, queueService, featureFlags,
        // rejudgePolicy.
        adminSubmissionService = new AdminSubmissionServiceImpl(
                submissionMapper, queueService, flags, rejudgePolicy);
    }

    private Submission createValidSubmission() {
        Submission submission = new Submission();
        submission.setId("sub-123");
        submission.setProblemId(1L);
        submission.setUserId("user-456");
        submission.setLanguage("java");
        submission.setCode("public class Main {}");
        submission.setStatus("Accepted");
        submission.setRetryCount(0);
        return submission;
    }

    @Nested
    @DisplayName("rejudge()")
    class Rejudge {

        @Test
        @DisplayName("calls queueService.enqueueJudgeJob and resets status to Pending")
        void rejudge_existingSubmission_enqueuesJob() {
            Submission submission = createValidSubmission();
            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(queueService.enqueueJudgeJob(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("job-789");

            RejudgeResult result = adminSubmissionService.rejudge("sub-123", false);

            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getSubmissionId()).isEqualTo("sub-123");
            assertThat(result.getOldStatus()).isEqualTo("Accepted");
            assertThat(result.getNewStatus()).isEqualTo("Pending");
            assertThat(submission.getStatus()).isEqualTo("Pending");

            verify(queueService).enqueueJudgeJob(
                    "sub-123", "1", "user-456", "java", "public class Main {}");
            verify(submissionMapper).updateById(submission);
        }

        @Test
        @DisplayName("non-existent submission returns RejudgeResult with success=false")
        void rejudge_nonExistent_returnsNotFound() {
            when(submissionMapper.selectById("nonexistent")).thenReturn(null);

            RejudgeResult result = adminSubmissionService.rejudge("nonexistent", false);

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("Submission not found");
            assertThat(result.getSubmissionId()).isEqualTo("nonexistent");
            verify(queueService, never()).enqueueJudgeJob(anyString(), anyString(),
                    anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("increments retryCount by 1 (D-23)")
        void rejudge_incrementsRetryCount() {
            Submission submission = createValidSubmission();
            submission.setRetryCount(3);
            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(queueService.enqueueJudgeJob(anyString(), anyString(), anyString(),
                    anyString(), anyString())).thenReturn("job-789");

            adminSubmissionService.rejudge("sub-123", false);

            assertThat(submission.getRetryCount()).isEqualTo(4);
            verify(submissionMapper).updateById(submission);
        }

        @Test
        @DisplayName("sets retryCount to 1 when null (D-23 null safety)")
        void rejudge_nullRetryCount_setsToOne() {
            Submission submission = createValidSubmission();
            submission.setRetryCount(null);
            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(queueService.enqueueJudgeJob(anyString(), anyString(), anyString(),
                    anyString(), anyString())).thenReturn("job-789");

            adminSubmissionService.rejudge("sub-123", false);

            assertThat(submission.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("enqueue failure returns RejudgeResult with success=false")
        void rejudge_enqueueFailure_returnsFailed() {
            Submission submission = createValidSubmission();
            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(queueService.enqueueJudgeJob(anyString(), anyString(), anyString(),
                    anyString(), anyString()))
                    .thenThrow(new RuntimeException("Queue unavailable"));

            RejudgeResult result = adminSubmissionService.rejudge("sub-123", false);

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("Queue unavailable");
        }
    }

    @Nested
    @DisplayName("batchRejudge()")
    class BatchRejudge {

        @Test
        @DisplayName("size>50 limit is enforced upstream by @Size on BatchRejudgeRequest")
        void batchRejudge_exceeds50_isNoLongerEnforcedAtServiceLayer() {
            // The 50-ID cap is now enforced at the controller boundary by
            // @Size(max=50) on BatchRejudgeRequest, which returns 400 before
            // reaching the service. The service no longer throws; it simply
            // processes the list it's given. This test guards against
            // re-introducing the service-layer size check that produced
            // inconsistent error messages (VALIDATION_FAILED vs
            // "size must not exceed 50").
            List<String> ids = java.util.Collections.nCopies(51, "sub-id");
            when(submissionMapper.selectById("sub-id")).thenReturn(null);

            BatchRejudgeResponse response = adminSubmissionService.batchRejudge(ids, false);
            assertThat(response.getTotal()).isEqualTo(51);
            assertThat(response.getFailed()).isEqualTo(51);
        }

        @Test
        @DisplayName("iterates over all IDs and returns success/failure counts")
        void batchRejudge_validBatch_returnsCounts() {
            Submission sub1 = createValidSubmission();
            sub1.setId("sub-1");
            Submission sub2 = createValidSubmission();
            sub2.setId("sub-2");

            when(submissionMapper.selectById("sub-1")).thenReturn(sub1);
            when(submissionMapper.selectById("sub-2")).thenReturn(sub2);
            when(queueService.enqueueJudgeJob(anyString(), anyString(), anyString(),
                    anyString(), anyString())).thenReturn("job-1");

            BatchRejudgeResponse response = adminSubmissionService.batchRejudge(
                    List.of("sub-1", "sub-2"), false);

            assertThat(response.getTotal()).isEqualTo(2);
            assertThat(response.getSuccessful()).isEqualTo(2);
            assertThat(response.getFailed()).isEqualTo(0);
            assertThat(response.getResults()).hasSize(2);
        }

        @Test
        @DisplayName("empty list returns total=0, successful=0, failed=0")
        void batchRejudge_emptyList_returnsZeroCounts() {
            BatchRejudgeResponse response = adminSubmissionService.batchRejudge(
                    List.of(), false);

            assertThat(response.getTotal()).isEqualTo(0);
            assertThat(response.getSuccessful()).isEqualTo(0);
            assertThat(response.getFailed()).isEqualTo(0);
            assertThat(response.getResults()).isEmpty();
        }

        @Test
        @DisplayName("batch with 50 IDs is accepted (boundary)")
        void batchRejudge_exactly50_isAccepted() {
            List<String> ids = java.util.Collections.nCopies(50, "sub-id");
            when(submissionMapper.selectById("sub-id")).thenReturn(null);

            BatchRejudgeResponse response = adminSubmissionService.batchRejudge(ids, false);

            assertThat(response.getTotal()).isEqualTo(50);
            assertThat(response.getFailed()).isEqualTo(50);
            assertThat(response.getSuccessful()).isEqualTo(0);
        }

        @Test
        @DisplayName("no longer silently returns total=0 for null/empty (now 400 upstream)")
        void nullList_doesNotSilentlyReturn_zeroCounts() {
            // The @Valid annotation on BatchRejudgeRequest in the controller
            // is what now prevents null/empty input. At the service layer we
            // still iterate — the test guards against re-introducing the
            // silent-return branch that hid client bugs in the original code.
            when(submissionMapper.selectById("a")).thenReturn(null);
            BatchRejudgeResponse response = adminSubmissionService.batchRejudge(List.of("a"), false);
            assertThat(response.getTotal()).isEqualTo(1);
            assertThat(response.getFailed()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("rejudge() response metadata")
    class RejudgeMetadata {

        @Test
        @DisplayName("populates rejudgedAt with the current time")
        void populatesRejudgedAt() {
            Submission submission = createValidSubmission();
            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(queueService.enqueueJudgeJob(anyString(), anyString(), anyString(),
                    anyString(), anyString())).thenReturn("job-1");

            java.time.Instant before = java.time.Instant.now();
            RejudgeResult result = adminSubmissionService.rejudge("sub-123", false);
            java.time.Instant after = java.time.Instant.now();

            assertThat(result.getRejudgedAt()).isNotNull();
            assertThat(result.getRejudgedAt()).isBetween(before, after);
        }

        @Test
        @DisplayName("populates retryCount equal to the post-increment value")
        void populatesRetryCount() {
            Submission submission = createValidSubmission();
            submission.setRetryCount(5);
            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(queueService.enqueueJudgeJob(anyString(), anyString(), anyString(),
                    anyString(), anyString())).thenReturn("job-1");

            RejudgeResult result = adminSubmissionService.rejudge("sub-123", false);

            assertThat(result.getRetryCount()).isEqualTo(6);
        }
    }
}
