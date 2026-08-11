package com.ulticode.modules.admin.service;

import com.ulticode.app.api.dto.RejudgeResultDTO;
import com.ulticode.app.api.service.SubmissionAdministrationService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.RejudgeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContestCutoverService + SubmissionCutoverService")
class ContestSubmissionCutoverServiceTest {

    @Mock private AdminSubmissionService adminSubmissionService;
    @Mock private SubmissionAdministrationService submissionDubbo;
    @Mock private CurrentUserProvider currentUserProvider;
    private SubmissionCutoverService submissionCutover;

    @BeforeEach
    void setUp() {
        submissionCutover = new SubmissionCutoverService(adminSubmissionService, currentUserProvider);
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        ReflectionTestUtils.setField(submissionCutover, "dubboProvider", submissionDubbo);
        ReflectionTestUtils.setField(submissionCutover, "dubboEnabled", false);
    }

    // ── SubmissionCutoverService ───────────────────────────────

    @Nested @DisplayName("flag=off (Submission)")
    class SubmissionLocal {
        @Test @DisplayName("rejudge delegates to adminSubmissionService")
        void rejudgeLocal() {
            RejudgeResult rr = new RejudgeResult();
            rr.setSubmissionId("s1"); rr.setSuccess(true);
            when(adminSubmissionService.rejudge("s1", true)).thenReturn(rr);

            RejudgeResult result = submissionCutover.rejudge("s1", true);

            assertThat(result).isSameAs(rr);
            verify(submissionDubbo, never()).rejudge(any());
        }
    }

    @Nested @DisplayName("flag=on (Submission)")
    class SubmissionDubbo {
        @BeforeEach void flagOn() {
            ReflectionTestUtils.setField(submissionCutover, "dubboEnabled", true);
        }

        @Test @DisplayName("rejudge writes via Dubbo, maps result back")
        void rejudgeViaDubbo() {
            when(submissionDubbo.rejudge(any())).thenReturn(
                    RpcResult.success(new RejudgeResultDTO("s1", "Pending",
                            Instant.now().toEpochMilli(), 1), "t-1"));

            RejudgeResult result = submissionCutover.rejudge("s1", true);

            verify(submissionDubbo).rejudge(any());
            assertThat(result.getSubmissionId()).isEqualTo("s1");
            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getNewStatus()).isEqualTo("Pending");
            assertThat(result.getRetryCount()).isEqualTo(1);
        }

        @Test @DisplayName("RPC error CONTENT_NOT_FOUND maps to BusinessException")
        void mapsNotFound() {
            when(submissionDubbo.rejudge(any())).thenReturn(
                    RpcResult.failure(new RpcResult.ErrorPayload("app", 40401, "not found"), "t-1"));
            assertThatThrownBy(() -> submissionCutover.rejudge("s1", false))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AdminErrorCode.SUBMISSION_NOT_FOUND);
        }
    }
}
