package com.ulticode.modules.admin.service;

import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.app.api.dto.RejudgeResultDTO;
import com.ulticode.app.api.service.ContestAdministrationService;
import com.ulticode.app.api.service.SubmissionAdministrationService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.admin.projection.AdminContestProjection;
import com.ulticode.modules.admin.service.AdminContestMutationService;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContestCutoverService + SubmissionCutoverService")
class ContestSubmissionCutoverServiceTest {

    // ── ContestCutoverService ──────────────────────────────────

    @Mock private AdminContestMutationService mutationService;
    @Mock private AdminContestProjection adminContestProjection;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private ContestAdministrationService contestDubbo;
    @Mock private SubmissionAdministrationService submissionDubbo;
    @Mock private AdminSubmissionService adminSubmissionService;

    private ContestCutoverService contestCutover;
    private SubmissionCutoverService submissionCutover;

    @BeforeEach
    void setUp() {
        contestCutover = new ContestCutoverService(mutationService, adminContestProjection, currentUserProvider);
        ReflectionTestUtils.setField(contestCutover, "dubboProvider", contestDubbo);
        ReflectionTestUtils.setField(contestCutover, "dubboEnabled", false);

        submissionCutover = new SubmissionCutoverService(adminSubmissionService);
        ReflectionTestUtils.setField(submissionCutover, "dubboProvider", submissionDubbo);
        ReflectionTestUtils.setField(submissionCutover, "dubboEnabled", false);
    }

    @Nested @DisplayName("flag=off (Contest)")
    class ContestLocal {
        @Test @DisplayName("createContest delegates to mutationService")
        void createLocal() {
            CreateContestDTO dto = new CreateContestDTO();
            dto.setSlug("icpc");
            dto.setTitle("ICPC");
            AdminContestVO vo = new AdminContestVO();
            vo.setId("c1");
            when(mutationService.createContest(dto, "user-1")).thenReturn(vo);

            AdminContestVO result = contestCutover.createContest(dto, "user-1");

            assertThat(result).isSameAs(vo);
            verify(contestDubbo, never()).createContest(any());
        }

        @Test @DisplayName("startContest delegates to mutationService")
        void startLocal() {
            AdminContestVO vo = new AdminContestVO();
            when(mutationService.startContest("c1")).thenReturn(vo);

            AdminContestVO result = contestCutover.startContest("c1");

            assertThat(result).isSameAs(vo);
            verify(contestDubbo, never()).startContest(any());
        }
    }

    @Nested @DisplayName("flag=on (Contest)")
    class ContestDubbo {
        @BeforeEach void flagOn() {
            ReflectionTestUtils.setField(contestCutover, "dubboEnabled", true);
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        }

        @Test @DisplayName("createContest writes via Dubbo, reads back via projection")
        void createViaDubbo() {
            CreateContestDTO dto = new CreateContestDTO();
            dto.setSlug("icpc"); dto.setTitle("ICPC");
            dto.setContestType("ICPC");
            dto.setStartTime(LocalDateTime.now(ZoneOffset.UTC));
            dto.setDuration(300);
            when(contestDubbo.createContest(any())).thenReturn(
                    RpcResult.success(new ContestAdminViewDTO("c1", "ICPC", "DRAFT"), "t-1"));
            AdminContestVO vo = new AdminContestVO();
            vo.setId("c1");
            when(adminContestProjection.getContest("c1")).thenReturn(vo);

            AdminContestVO result = contestCutover.createContest(dto, "admin-1");

            verify(contestDubbo).createContest(any());
            verify(adminContestProjection).getContest("c1");
            assertThat(result.getId()).isEqualTo("c1");
        }

        @Test @DisplayName("deleteContest writes via Dubbo")
        void deleteViaDubbo() {
            when(contestDubbo.deleteContest(any())).thenReturn(RpcResult.success("t-1"));
            contestCutover.deleteContest("c1");
            verify(contestDubbo).deleteContest(any());
            verify(mutationService, never()).deleteContest(anyString());
        }

        @Test @DisplayName("RPC error maps to BusinessException")
        void mapsError() {
            when(contestDubbo.startContest(any())).thenReturn(
                    RpcResult.failure(new RpcResult.ErrorPayload("app", 40902, "conflict"), "t-1"));
            assertThatThrownBy(() -> contestCutover.startContest("c1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        }
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
                    .extracting("errorCode").isEqualTo(ErrorCode.PROBLEM_NOT_FOUND);
        }
    }
}
