package com.ulticode.modules.moderation.port.adapter;

import com.ulticode.app.user.port.UserFactsProjection;
import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationAccountAdapterTest {

    @Mock private UserFactsProjection userFactsProjection;
    @Mock private AccountQueryService accountQueryService;
    @Mock private AccountAdministrationService accountAdministrationService;

    private ModerationAccountAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ModerationAccountAdapter(userFactsProjection);
        adapter.setAccountQueryService(accountQueryService);
        adapter.setAccountAdministrationService(accountAdministrationService);
    }

    @Test
    void updateBanStatusDelegatesToAuthOwnerWithVersionAndActor() {
        AuthAccountDTO current = new AuthAccountDTO(
                "u-1", "alice", "alice@example.test", "USER", true, false,
                null, null, LocalDateTime.parse("2026-08-19T00:00:00"), null, 7L);
        when(accountQueryService.getAccountById("u-1")).thenReturn(RpcResult.success(current, "t-1"));
        when(accountAdministrationService.changeState(org.mockito.ArgumentMatchers.any())).thenReturn(
                RpcResult.success(new AccountStateDTO("u-1", true, true, 8L), "t-1"));

        adapter.updateBanStatus("u-1", true, "spam", "moderator-1", "action-1");

        ArgumentCaptor<ChangeAccountStateCommand> captor =
                ArgumentCaptor.forClass(ChangeAccountStateCommand.class);
        verify(accountAdministrationService).changeState(captor.capture());
        ChangeAccountStateCommand command = captor.getValue();
        assertThat(command.accountId()).isEqualTo("u-1");
        assertThat(command.expectedVersion()).isEqualTo(7L);
        assertThat(command.action()).isEqualTo(ChangeAccountStateCommand.AccountStateAction.BAN);
        assertThat(command.actor().actorId()).isEqualTo("moderator-1");
        assertThat(command.idempotency().idempotencyKey()).isEqualTo("action-1");
    }

    @Test
    void updateBanStatusRejectsMissingActorMetadataBeforeRpc() {
        assertThatThrownBy(() -> adapter.updateBanStatus("u-1", true, "spam", null, "action-1"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(accountQueryService, accountAdministrationService);
    }

    @Test
    void updateBanStatusFailsClosedWhenAuthOwnerIsUnavailable() {
        adapter.setAccountAdministrationService(null);
        assertThatThrownBy(() ->
                adapter.updateBanStatus("u-1", true, "spam", "moderator-1", "action-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Auth account owner unavailable");
    }

    @Test
    void updateBanStatusDoesNotTranslateAuthOutageToNotFound() {
        when(accountQueryService.getAccountById("u-1"))
                .thenReturn(RpcResult.failure(com.ulticode.auth.api.error.AuthErrorCode.UNEXPECTED_AUTH_STATE, "t-1"));

        assertThatThrownBy(() ->
                adapter.updateBanStatus("u-1", true, "spam", "moderator-1", "action-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Auth account owner unavailable");
    }
}
