package com.ulticode.app.dubbo.provider;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.DeleteContestCommand;
import com.ulticode.app.api.command.EndContestCommand;
import com.ulticode.app.api.command.StartContestCommand;
import com.ulticode.app.api.command.UpdateContestCommand;
import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.contest.port.ContestOwnerPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContestAdministrationProvider")
class ContestAdministrationProviderTest {

    @Mock
    private ContestOwnerPort ownerPort;

    @Mock
    private ContestAdminReadPort readPort;

    @Mock
    private AppCommandReceiptMapper receiptMapper;

    private ContestAdministrationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ContestAdministrationProvider(ownerPort, readPort, receiptMapper, new ObjectMapper());
    }

    private static ActorDelegation adminActor() {
        return new ActorDelegation("ADMIN", "admin-1", null, null);
    }

    private static ActorDelegation userActor() {
        return new ActorDelegation("USER", "user-1", null, null);
    }

    private static TraceMetadata trace() {
        return new TraceMetadata("t-12345", null, null, null);
    }

    private static IdMetadata idempotency(String key) {
        return IdMetadata.of(key, "fingerprint");
    }

    private static ContestAdminDTO contestView(String id, String title, String status) {
        ContestAdminDTO view = new ContestAdminDTO();
        view.setId(id);
        view.setTitle(title);
        view.setStatus(status);
        return view;
    }

    @Nested
    @DisplayName("owner command routing")
    class Routing {

        @Test
        @DisplayName("create forwards creator and returns owner confirmation")
        void create() {
            CreateContestCommand command = new CreateContestCommand(
                    UUID.randomUUID().toString(), idempotency("create-key"), adminActor(), trace(),
                    "weekly-1", "Weekly 1", "author-1", "ICPC", "SCORE", null, "desc",
                    System.currentTimeMillis() + 3600000L, 120);
            when(ownerPort.createContest(any())).thenReturn("c-100");
            when(readPort.selectById("c-100")).thenReturn(contestView("c-100", "Weekly 1", "UPCOMING"));

            RpcResult<ContestAdminViewDTO> result = provider.createContest(command);

            assertThat(result.success()).isTrue();
            assertThat(result.data()).isEqualTo(new ContestAdminViewDTO("c-100", "Weekly 1", "UPCOMING"));
            verify(ownerPort).createContest(any());
        }

        @Test
        @DisplayName("update forwards editable fields to owner")
        void update() {
            UpdateContestCommand command = new UpdateContestCommand(
                    UUID.randomUUID().toString(), idempotency("update-key"), adminActor(), trace(),
                    "c-10", 1L, "New Title", System.currentTimeMillis(), 180, "rationale");
            when(readPort.selectById("c-10")).thenReturn(contestView("c-10", "New Title", "UPCOMING"));

            RpcResult<ContestAdminViewDTO> result = provider.updateContest(command);

            assertThat(result.success()).isTrue();
            verify(ownerPort).updateContest(any());
        }

        @Test
        @DisplayName("lifecycle and delete commands use owner port")
        void lifecycle() {
            when(readPort.selectById("c-10")).thenReturn(contestView("c-10", "Weekly", "RUNNING"));
            StartContestCommand start = new StartContestCommand(
                    UUID.randomUUID().toString(), idempotency("start-key"), adminActor(), trace(),
                    "c-10", 1L, "starting");
            EndContestCommand end = new EndContestCommand(
                    UUID.randomUUID().toString(), idempotency("end-key"), adminActor(), trace(),
                    "c-10", 1L, "ending");
            DeleteContestCommand delete = new DeleteContestCommand(
                    UUID.randomUUID().toString(), idempotency("delete-key"), adminActor(), trace(),
                    "c-10", 1L, "deleting");

            assertThat(provider.startContest(start).success()).isTrue();
            assertThat(provider.endContest(end).success()).isTrue();
            assertThat(provider.deleteContest(delete).success()).isTrue();

            verify(ownerPort).startContest("c-10");
            verify(ownerPort).endContest("c-10");
            verify(ownerPort).deleteContest("c-10", "admin-1");
        }
    }

    @Test
    @DisplayName("non-admin actor is rejected before owner mutation")
    void rejectsNonAdminActor() {
        StartContestCommand command = new StartContestCommand(
                UUID.randomUUID().toString(), idempotency("forbidden-key"), userActor(), trace(),
                "c-10", 1L, "starting");

        RpcResult<ContestAdminViewDTO> result = provider.startContest(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
        verifyNoInteractions(ownerPort, readPort);
    }

    @Test
    @DisplayName("contest lifecycle error maps explicitly to bad request")
    void mapsLifecycleError() {
        StartContestCommand command = new StartContestCommand(
                UUID.randomUUID().toString(), idempotency("state-key"), adminActor(), trace(),
                "c-10", 1L, "starting");
        org.mockito.Mockito.doThrow(new BusinessException(
                        com.ulticode.app.error.ContestErrorCode.CONTEST_NOT_STARTED))
                .when(ownerPort).startContest("c-10");

        RpcResult<ContestAdminViewDTO> result = provider.startContest(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.BAD_REQUEST.code());
        verifyNoInteractions(readPort);
    }
}
