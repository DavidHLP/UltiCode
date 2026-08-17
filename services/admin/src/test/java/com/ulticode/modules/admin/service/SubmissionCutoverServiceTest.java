package com.ulticode.modules.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.submission.api.dto.BatchRejudgeResultDTO;
import com.ulticode.submission.api.dto.RejudgeResultDTO;
import com.ulticode.submission.api.service.SubmissionAdministrationService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.BatchRejudgeResponse;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.admin.service.SubmissionCutoverService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SubmissionCutoverService#batchRejudge}.
 *
 * <p>ADMIN-008: the notification half of the former
 * {@code NotificationCutoverServiceTest} was removed with the deleted
 * {@code NotificationCutoverService}; this suite retains the
 * {@code SubmissionCutoverService.batchRejudge} coverage, owned by
 * AdminSubmissionMigration.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubmissionCutoverService.batchRejudge")
class SubmissionCutoverServiceTest {

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

    @Nested
    @DisplayName("batchRejudge")
    class BatchRejudge {

        @Test
        @DisplayName("flag=off delegates to the local submission service")
        void delegatesLocal() {
            BatchRejudgeResponse resp = new BatchRejudgeResponse();
            resp.setTotal(2);
            resp.setSuccessful(2);
            resp.setFailed(0);
            when(adminSubmissionService.batchRejudge(anyList(), anyBoolean())).thenReturn(resp);

            BatchRejudgeResponse result = submissionCutover.batchRejudge(List.of("s1", "s2"), false);

            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getSuccessful()).isEqualTo(2);
            assertThat(result.getFailed()).isEqualTo(0);
            verify(submissionDubbo, never()).batchRejudge(any());
        }

        @Test
        @DisplayName("flag=on writes via Dubbo and maps the DTO")
        void viaDubbo() {
            ReflectionTestUtils.setField(submissionCutover, "dubboEnabled", true);
            when(submissionDubbo.batchRejudge(any())).thenReturn(
                    RpcResult.success(new BatchRejudgeResultDTO(2, 1, 1,
                            List.of(new RejudgeResultDTO("s1", "ACCEPTED", Instant.now().toEpochMilli(), 0))),
                            "t-1"));

            BatchRejudgeResponse result = submissionCutover.batchRejudge(List.of("s1", "s2"), false);

            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getSuccessful()).isEqualTo(1);
            assertThat(result.getFailed()).isEqualTo(1);
            assertThat(result.getResults()).hasSize(1);
            verify(adminSubmissionService, never()).batchRejudge(anyList(), anyBoolean());
        }

        @Test
        @DisplayName("flag=on treats a legacy payload without success as successful")
        void acceptsLegacyProviderPayload() throws Exception {
            ReflectionTestUtils.setField(submissionCutover, "dubboEnabled", true);
            RejudgeResultDTO legacy = new ObjectMapper().readValue(
                    """
                    {"submissionId":"s1","newStatus":"PENDING",
                     "rejudgedAtEpochMs":1700000000000,"retryCount":1,
                     "errorCode":null,"error":null}
                    """,
                    RejudgeResultDTO.class);
            when(submissionDubbo.rejudge(any())).thenReturn(RpcResult.success(legacy, "t-1"));
            RejudgeResult result = submissionCutover.rejudge("s1", false);

            assertThat(result.getSubmissionId()).isEqualTo("s1");
            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getNewStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("flag=on maps provider transport failure to a generic admin error")
        void mapsProviderUnavailable() {
            ReflectionTestUtils.setField(submissionCutover, "dubboEnabled", true);
            when(submissionDubbo.batchRejudge(any()))
                    .thenThrow(new RuntimeException("transport details"));

            assertThatThrownBy(() -> submissionCutover.batchRejudge(List.of("s1"), false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Submission provider unavailable");
        }
    }
}
