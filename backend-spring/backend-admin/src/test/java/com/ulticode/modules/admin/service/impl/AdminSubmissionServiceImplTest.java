package com.ulticode.modules.admin.service.impl;

import com.ulticode.modules.submission.dto.BatchRejudgeResponse;
import com.ulticode.modules.submission.dto.RejudgeResult;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.admin.port.AdminSubmissionReadPort;
import com.ulticode.modules.submission.port.RejudgePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AdminSubmissionServiceImpl} after architecture-review #3.
 *
 * <p>The service is now a 3-line dispatch: load submission, build the
 * result DTO with the old status, delegate to {@link RejudgePolicy#rejudge}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminSubmissionServiceImpl")
class AdminSubmissionServiceImplTest {

    @Mock
    private AdminSubmissionReadPort submissionReadPort;

    @Mock
    private RejudgePolicy rejudgePolicy;

    private AdminSubmissionServiceImpl adminSubmissionService;

    @BeforeEach
    void setUp() {
        adminSubmissionService = new AdminSubmissionServiceImpl(submissionReadPort, rejudgePolicy);
    }

    private Submission createValidSubmission() {
        Submission submission = new Submission();
        submission.setId("sub-123");
        submission.setStatus("Accepted");
        submission.setLanguage("cpp");
        return submission;
    }

    @Nested
    @DisplayName("rejudge()")
    class Rejudge {

        @Test
        @DisplayName("non-existent submission returns success=false without delegating")
        void rejudge_nonExistent_returnsNotFoundAndSkipsPolicy() {
            when(submissionReadPort.findById("nonexistent")).thenReturn(null);

            RejudgeResult result = adminSubmissionService.rejudge("nonexistent", false);

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("Submission not found");
            assertThat(result.getSubmissionId()).isEqualTo("nonexistent");
            verify(rejudgePolicy, never()).rejudge(any(), any());
        }

        @Test
        @DisplayName("delegates to the policy with oldStatus captured and returns its result")
        void rejudge_existingSubmission_delegatesToPolicy() {
            Submission submission = createValidSubmission();
            when(submissionReadPort.findById("sub-123")).thenReturn(submission);
            com.ulticode.modules.admin.dto.RejudgeResult policyResult = new com.ulticode.modules.admin.dto.RejudgeResult();
            policyResult.setSubmissionId("sub-123");
            policyResult.setSuccess(true);
            policyResult.setNewStatus("Pending");
            when(rejudgePolicy.rejudge(eq(submission), any(com.ulticode.modules.admin.dto.RejudgeResult.class)))
                .thenAnswer(inv -> {
                    com.ulticode.modules.admin.dto.RejudgeResult passed = inv.getArgument(1);
                    assertThat(passed.getOldStatus()).isEqualTo("Accepted");
                    return policyResult;
                });

            RejudgeResult result = adminSubmissionService.rejudge("sub-123", false);

            assertThat(result.getSubmissionId()).isEqualTo("sub-123");
            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getNewStatus()).isEqualTo("Pending");
            verify(rejudgePolicy).rejudge(eq(submission), any(com.ulticode.modules.admin.dto.RejudgeResult.class));
        }
    }

    @Nested
    @DisplayName("batchRejudge()")
    class BatchRejudge {

        @Test
        @DisplayName("size>50 cap is enforced upstream by @Size on BatchRejudgeRequest")
        void batchRejudge_exceeds50_isNoLongerEnforcedAtServiceLayer() {
            List<String> ids = java.util.Collections.nCopies(51, "sub-id");
            when(submissionReadPort.findById("sub-id")).thenReturn(null);

            BatchRejudgeResponse response = adminSubmissionService.batchRejudge(ids, false);

            assertThat(response.getTotal()).isEqualTo(51);
            assertThat(response.getFailed()).isEqualTo(51);
            verify(rejudgePolicy, never()).rejudge(any(), any());
        }

        @Test
        @DisplayName("iterates over all IDs and returns success/failure counts")
        void batchRejudge_validBatch_returnsCounts() {
            Submission sub1 = createValidSubmission();
            sub1.setId("sub-1");
            Submission sub2 = createValidSubmission();
            sub2.setId("sub-2");

            when(submissionReadPort.findById("sub-1")).thenReturn(sub1);
            when(submissionReadPort.findById("sub-2")).thenReturn(sub2);
            when(rejudgePolicy.rejudge(any(Submission.class), any(com.ulticode.modules.admin.dto.RejudgeResult.class)))
                .thenAnswer(inv -> {
                    com.ulticode.modules.admin.dto.RejudgeResult r = inv.getArgument(1);
                    r.setSuccess(true);
                    return r;
                });

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
            verify(rejudgePolicy, never()).rejudge(any(), any());
        }

        @Test
        @DisplayName("mixed success and failure accumulates counts correctly")
        void batchRejudge_mixedOutcome_accumulatesCorrectly() {
            Submission sub1 = createValidSubmission();
            sub1.setId("sub-1");

            when(submissionReadPort.findById("sub-1")).thenReturn(sub1);
            when(submissionReadPort.findById("sub-2")).thenReturn(null);
            when(rejudgePolicy.rejudge(eq(sub1), any(com.ulticode.modules.admin.dto.RejudgeResult.class)))
                .thenAnswer(inv -> {
                    com.ulticode.modules.admin.dto.RejudgeResult r = inv.getArgument(1);
                    r.setSuccess(true);
                    return r;
                });

            BatchRejudgeResponse response = adminSubmissionService.batchRejudge(
                List.of("sub-1", "sub-2"), false);

            assertThat(response.getTotal()).isEqualTo(2);
            assertThat(response.getSuccessful()).isEqualTo(1);
            assertThat(response.getFailed()).isEqualTo(1);
            assertThat(response.getResults()).hasSize(2);
            assertThat(response.getResults().get(0).getSuccess()).isTrue();
            assertThat(response.getResults().get(1).getSuccess()).isFalse();
        }
    }
}
