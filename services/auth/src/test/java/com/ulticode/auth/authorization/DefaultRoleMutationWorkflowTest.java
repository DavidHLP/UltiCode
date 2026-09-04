package com.ulticode.auth.authorization;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangeRoleCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.common.audit.AuditSinkPort;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultRoleMutationWorkflowTest {

    @Mock
    private AuthAccountPort authAccountPort;
    @Mock
    private AuditSinkPort auditSinkPort;

    private DefaultRoleMutationWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new DefaultRoleMutationWorkflow(authAccountPort, auditSinkPort);
    }

    @Test
    void roleChangeUsesCasAndNeverMarksAccountDeleted() {
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account("USER", 2L)));
        when(authAccountPort.updateAccountIfVersion(
                "user-1", true, false, "ADMIN", 2L)).thenReturn(true);

        RpcResult<AccountMutationDTO> result = workflow.changeRole(command("ADMIN", 2L));

        assertThat(result.success()).isTrue();
        assertThat(result.data().role()).isEqualTo("ADMIN");
        assertThat(result.data().authzVersion()).isEqualTo(3L);
        assertThat(result.data().deleted()).isFalse();
        verify(authAccountPort).updateAccountIfVersion(
                "user-1", true, false, "ADMIN", 2L);
        verify(auditSinkPort).log(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void staleRoleChangeFailsClosedBeforeCas() {
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account("USER", 3L)));

        RpcResult<AccountMutationDTO> result = workflow.changeRole(command("ADMIN", 2L));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(
                AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT.code());
        verify(authAccountPort, never()).updateAccountIfVersion(
                any(), anyBoolean(), anyBoolean(), any(), anyLong());
        verify(auditSinkPort, never()).log(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static ChangeRoleCommand command(String role, long version) {
        return new ChangeRoleCommand(
                "cmd-role", IdMetadata.of("key-role", null),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "role test"),
                new TraceMetadata("trace-role", null, null, null),
                "user-1", role, version, "role test");
    }

    private static AuthAccountRecord account(String role, long version) {
        return new AuthAccountRecord(
                "user-1", "alice", "alice@example.com", "secret", role,
                true, false, null, null, version);
    }
}
