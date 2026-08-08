package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.DeleteContestCommand;
import com.ulticode.app.api.command.EndContestCommand;
import com.ulticode.app.api.command.StartContestCommand;
import com.ulticode.app.api.command.UpdateContestCommand;
import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.service.ContestAdministrationDomainService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContestAdministrationProvider")
class ContestAdministrationProviderTest {

    @Mock
    private ContestAdministrationDomainService domainService;

    private ContestAdministrationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ContestAdministrationProvider(domainService);
    }

    private static ActorDelegation adminActor() {
        return new ActorDelegation("ADMIN", "admin-1", null, null);
    }

    private static Contest contestEntity(String id, String slug, String title, ContestStatus status) {
        Contest c = new Contest();
        c.setId(id);
        c.setSlug(slug);
        c.setTitle(title);
        c.setStatus(status.name());
        return c;
    }

    private static TraceMetadata trace() {
        return new TraceMetadata("t-12345", null, null, null);
    }

    @Nested
    @DisplayName("createContest")
    class Create {

        @Test
        @DisplayName("successful creation returns admin view")
        void success() {
            CreateContestCommand command = new CreateContestCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "weekly-1", "Weekly 1", "author-1", "ICPC", "SCORE", null, "desc",
                    System.currentTimeMillis() + 3600000L, 120);

            Contest entity = contestEntity("c-100", "weekly-1", "Weekly 1", ContestStatus.UPCOMING);
            when(domainService.createContest(any(CreateContestDTO.class), eq("author-1")))
                    .thenReturn(entity);

            RpcResult<ContestAdminViewDTO> result = provider.createContest(command);

            assertThat(result.success()).isTrue();
            assertThat(result.data().contestId()).isEqualTo("c-100");
            assertThat(result.data().title()).isEqualTo("Weekly 1");
        }

        @Test
        @DisplayName("domain conflict exception maps to CONTENT_STATE_CONFLICT")
        void conflict() {
            CreateContestCommand command = new CreateContestCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "dup", "Duplicate", "author-1", "ICPC", "SCORE", null, "desc",
                    System.currentTimeMillis() + 3600000L, 120);

            when(domainService.createContest(any(CreateContestDTO.class), any()))
                    .thenThrow(new BusinessException(BaseErrorCode.CONFLICT, "Slug duplicate"));

            RpcResult<ContestAdminViewDTO> result = provider.createContest(command);

            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_STATE_CONFLICT.code());
        }
    }

    @Nested
    @DisplayName("updateContest")
    class Update {

        @Test
        @DisplayName("successful update returns admin view")
        void success() {
            UpdateContestCommand command = new UpdateContestCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "c-10", 1L, "New Title", System.currentTimeMillis(), 180, "rationale");

            Contest entity = contestEntity("c-10", "weekly-1", "New Title", ContestStatus.UPCOMING);
            when(domainService.updateContest(eq("c-10"), any(UpdateContestDTO.class), eq("admin-1")))
                    .thenReturn(entity);

            RpcResult<ContestAdminViewDTO> result = provider.updateContest(command);

            assertThat(result.success()).isTrue();
            assertThat(result.data().contestId()).isEqualTo("c-10");
        }
    }

    @Nested
    @DisplayName("lifecycle commands")
    class Lifecycle {

        @Test
        @DisplayName("startContest delegates to domainService")
        void start() {
            StartContestCommand command = new StartContestCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "c-10", 1L, "starting");

            Contest entity = contestEntity("c-10", "weekly-1", "Weekly 1", ContestStatus.RUNNING);
            when(domainService.startContest("c-10", "admin-1")).thenReturn(entity);

            RpcResult<ContestAdminViewDTO> result = provider.startContest(command);

            assertThat(result.success()).isTrue();
            verify(domainService).startContest("c-10", "admin-1");
        }

        @Test
        @DisplayName("endContest delegates to domainService")
        void end() {
            EndContestCommand command = new EndContestCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "c-10", 1L, "ending");

            Contest entity = contestEntity("c-10", "weekly-1", "Weekly 1", ContestStatus.FINISHED);
            when(domainService.endContest("c-10", "admin-1")).thenReturn(entity);

            RpcResult<ContestAdminViewDTO> result = provider.endContest(command);

            assertThat(result.success()).isTrue();
            verify(domainService).endContest("c-10", "admin-1");
        }

        @Test
        @DisplayName("deleteContest delegates to domainService")
        void delete() {
            DeleteContestCommand command = new DeleteContestCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "c-10", 1L, "deleting");

            RpcResult<Void> result = provider.deleteContest(command);

            assertThat(result.success()).isTrue();
            verify(domainService).deleteContest("c-10", "admin-1");
        }
    }
}
