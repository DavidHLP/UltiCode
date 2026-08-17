package com.ulticode.modules.admin.service.impl;

import com.ulticode.submission.api.dto.SubmissionAdminRowDTO;
import com.ulticode.submission.api.service.RejudgePolicy;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import com.ulticode.modules.admin.dto.BatchRejudgeResponse;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.admin.service.AdminSubmissionService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AdminSubmissionServiceImpl} after architecture-review #3.
 *
 * <p>The service is now a 3-line dispatch: load submission, build the
 * result DTO with the old status, delegate to {@link RejudgePolicy#rejudge}.
 *
 * <p>P7-FIX-ADMIN-CONSUMERS-001: {@code RejudgePolicy.rejudge} signature
 * changed from {@code (Submission, admin.dto.RejudgeResult)} to
 * {@code (String, app.api.dto.RejudgeResult)} during SUBMISSION relocation.
 * Mocks now stub the new signature; the impl bridges the legacy return type
 * via {@code toDomain}.
 *
 * <p>ADMIN-004: the old-status read goes through the entity-free
 * {@link SubmissionAdminReadPort} contract ({@link SubmissionAdminRowDTO})
 * instead of the submission entity.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminSubmissionServiceImpl")
class AdminSubmissionServiceImplTest {

    @Mock
    private SubmissionAdminReadPort submissionReadPort;

    @Mock
    private RejudgePolicy rejudgePolicy;

    private AdminSubmissionService adminSubmissionService;

    @BeforeEach
    void setUp() {
        adminSubmissionService = new AdminSubmissionServiceImpl(submissionReadPort, rejudgePolicy);
    }

    private SubmissionAdminRowDTO createValidSubmission() {
        return new SubmissionAdminRowDTO(
                "sub-123", 1L, "user-1", "cpp", "Accepted",
                10, 5.0, null, 4, null, null, null, null,
                List.of(), null, null);
    }

    @Nested
    @DisplayName("rejudge()")
    class Rejudge {

        @Test
        @DisplayName("non-existent submission returns failure result without touching the policy")
        void rejudge_nonExistent_returnsFailureResult() {
            when(submissionReadPort.findById("nonexistent")).thenReturn(null);

            RejudgeResult result = adminSubmissionService.rejudge("nonexistent", true);

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("Submission not found");
            assertThat(result.getSubmissionId()).isEqualTo("nonexistent");
        }

        @Test
        @DisplayName("delegates to policy with oldStatus captured and returns its result")
        void rejudge_existingSubmission_delegatesToPolicy() {
            SubmissionAdminRowDTO submission = createValidSubmission();
            when(submissionReadPort.findById("sub-123")).thenReturn(submission);
            com.ulticode.submission.api.dto.RejudgeResult policyResult = new com.ulticode.submission.api.dto.RejudgeResult();
            policyResult.setSubmissionId("sub-123");
            policyResult.setSuccess(true);
            policyResult.setNewStatus("Pending");
            when(rejudgePolicy.rejudge(eq("sub-123"), any(com.ulticode.submission.api.dto.RejudgeResult.class)))
                .thenAnswer(inv -> {
                    com.ulticode.submission.api.dto.RejudgeResult passed = inv.getArgument(1);
                    assertThat(passed.getOldStatus()).isEqualTo("Accepted");
                    return policyResult;
                });

            RejudgeResult result = adminSubmissionService.rejudge("sub-123", false);

            assertThat(result.getSubmissionId()).isEqualTo("sub-123");
            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getNewStatus()).isEqualTo("Pending");
            verify(rejudgePolicy).rejudge(eq("sub-123"), any(com.ulticode.submission.api.dto.RejudgeResult.class));
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

            BatchRejudgeResponse response = adminSubmissionService.batchRejudge(ids, true);

            assertThat(response.getTotal()).isEqualTo(51);
            assertThat(response.getFailed()).isEqualTo(51);
        }

        @Test
        @DisplayName("iterates IDs and aggregates success/failure")
        void batchRejudge_iteratesIds() {
            SubmissionAdminRowDTO sub1 = createValidSubmission();
            SubmissionAdminRowDTO sub2 = new SubmissionAdminRowDTO(
                    "sub-2", 2L, "user-2", "python", "Wrong Answer",
                    20, 8.0, null, 7, null, null, null, null,
                    List.of(), null, null);
            when(submissionReadPort.findById("sub-1")).thenReturn(sub1);
            when(submissionReadPort.findById("sub-2")).thenReturn(sub2);
            when(rejudgePolicy.rejudge(any(String.class), any(com.ulticode.submission.api.dto.RejudgeResult.class)))
                .thenAnswer(inv -> {
                    com.ulticode.submission.api.dto.RejudgeResult r = inv.getArgument(1);
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
        @DisplayName("empty batch yields total=0, successful=0, failed=0")
        void batchRejudge_empty() {
            BatchRejudgeResponse response = adminSubmissionService.batchRejudge(List.of(), false);

            assertThat(response.getTotal()).isEqualTo(0);
            assertThat(response.getSuccessful()).isEqualTo(0);
            assertThat(response.getFailed()).isEqualTo(0);
            assertThat(response.getResults()).isEmpty();
        }

        @Test
        @DisplayName("mixed success/failure is aggregated per-submission")
        void batchRejudge_mixed() {
            SubmissionAdminRowDTO sub1 = createValidSubmission();
            when(submissionReadPort.findById("sub-1")).thenReturn(sub1);
            when(submissionReadPort.findById("sub-2")).thenReturn(null);
            when(rejudgePolicy.rejudge(eq("sub-1"), any(com.ulticode.submission.api.dto.RejudgeResult.class)))
                .thenAnswer(inv -> {
                    com.ulticode.submission.api.dto.RejudgeResult r = inv.getArgument(1);
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
