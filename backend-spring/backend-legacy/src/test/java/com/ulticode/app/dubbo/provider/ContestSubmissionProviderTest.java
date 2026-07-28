package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.StartContestCommand;
import com.ulticode.app.api.command.RejudgeCommand;
import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.app.api.dto.RejudgeResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.admin.service.AdminContestMutationService;
import com.ulticode.modules.admin.service.AdminSubmissionService;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContestAdministrationProvider")
class ContestAdministrationProviderTest {

    @Mock private AdminContestMutationService mutationService;
    private ContestAdministrationProvider provider;

    @BeforeEach
    void setUp() { provider = new ContestAdministrationProvider(mutationService); }

    private static ActorDelegation actor() {
        return new ActorDelegation("ADMIN", "admin-1", "admin-1", "test");
    }

    @Nested @DisplayName("createContest()")
    class Create {
        @Test @DisplayName("maps command to DTO and returns admin view")
        void createsContest() {
            AdminContestVO vo = new AdminContestVO();
            vo.setId("contest-1");
            vo.setTitle("ICPC 2026");
            vo.setStatus("DRAFT");
            when(mutationService.createContest(any(CreateContestDTO.class), anyString())).thenReturn(vo);

            var cmd = new CreateContestCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                    "icpc-2026", "ICPC 2026", "admin-1", "ICPC", "ICPC",
                    null, null, System.currentTimeMillis() + 86400000L, 300);
            RpcResult<ContestAdminViewDTO> result = provider.createContest(cmd);

            assertThat(result.success()).isTrue();
            assertThat(result.data().contestId()).isEqualTo("contest-1");
            assertThat(result.data().title()).isEqualTo("ICPC 2026");
            assertThat(result.data().status()).isEqualTo("DRAFT");
        }

        @Test @DisplayName("maps BusinessException(CONTEST_NOT_FOUND) to CONTENT_NOT_FOUND")
        void mapsNotFound() {
            when(mutationService.createContest(any(), anyString()))
                    .thenThrow(new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
            var cmd = new CreateContestCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                    "x", "X", "a", "ICPC", "ICPC", null, null, System.currentTimeMillis(), 120);
            RpcResult<ContestAdminViewDTO> result = provider.createContest(cmd);
            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_NOT_FOUND.code());
        }
    }

    @Nested @DisplayName("startContest()")
    class Start {
        @Test @DisplayName("delegates to mutationService.startContest")
        void starts() {
            AdminContestVO vo = new AdminContestVO();
            vo.setId("c1"); vo.setTitle("Contest"); vo.setStatus("RUNNING");
            when(mutationService.startContest("c1")).thenReturn(vo);

            var cmd = new StartContestCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                    "c1", 0L, "start");
            RpcResult<ContestAdminViewDTO> result = provider.startContest(cmd);
            assertThat(result.success()).isTrue();
            assertThat(result.data().status()).isEqualTo("RUNNING");
        }
    }

    @Nested @DisplayName("deleteContest()")
    class Delete {
        @Test @DisplayName("delegates and returns success")
        void deletes() {
            var cmd = new com.ulticode.app.api.command.DeleteContestCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                    "c1", 0L, "delete");
            RpcResult<Void> result = provider.deleteContest(cmd);
            assertThat(result.success()).isTrue();
            verify(mutationService).deleteContest("c1");
        }
    }
}

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubmissionAdministrationProvider")
class SubmissionAdministrationProviderTest {

    @Mock private AdminSubmissionService submissionService;
    private SubmissionAdministrationProvider provider;

    @BeforeEach
    void setUp() { provider = new SubmissionAdministrationProvider(submissionService); }

    @Test @DisplayName("maps rejudge result to RejudgeResultDTO")
    void rejudges() {
        RejudgeResult rr = new RejudgeResult();
        rr.setSubmissionId("sub-1");
        rr.setSuccess(true);
        rr.setNewStatus("Pending");
        rr.setRejudgedAt(Instant.now());
        rr.setRetryCount(2);
        when(submissionService.rejudge("sub-1", true)).thenReturn(rr);

        var cmd = new RejudgeCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(),
                new ActorDelegation("ADMIN", "admin", "admin", "test"),
                TraceMetadata.EMPTY, "sub-1", true);
        RpcResult<RejudgeResultDTO> result = provider.rejudge(cmd);

        assertThat(result.success()).isTrue();
        assertThat(result.data().submissionId()).isEqualTo("sub-1");
        assertThat(result.data().newStatus()).isEqualTo("Pending");
        assertThat(result.data().retryCount()).isEqualTo(2);
    }

    @Test @DisplayName("maps success=false to CONTENT_NOT_FOUND")
    void mapsNotFoundResult() {
        RejudgeResult rr = new RejudgeResult();
        rr.setSubmissionId("missing");
        rr.setSuccess(false);
        rr.setError("not found");
        when(submissionService.rejudge("missing", false)).thenReturn(rr);

        var cmd = new RejudgeCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(),
                new ActorDelegation("ADMIN", "admin", "admin", "test"),
                TraceMetadata.EMPTY, "missing", false);
        RpcResult<RejudgeResultDTO> result = provider.rejudge(cmd);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_NOT_FOUND.code());
    }
}
