package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.idempotency.CommandReceiptExecutor;
import com.ulticode.auth.security.InternalDelegationAssertionVerifier;
import com.ulticode.auth.service.AccountAdministrationWorkflow;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountAdministrationProviderContractTest {

    private AccountAdministrationWorkflow workflow;
    private CommandReceiptExecutor receiptExecutor;
    private InternalDelegationAssertionVerifier delegationVerifier;
    private AccountAdministrationProvider provider;

    @BeforeEach
    void setUp() {
        workflow = mock(AccountAdministrationWorkflow.class);
        receiptExecutor = mock(CommandReceiptExecutor.class);
        delegationVerifier = mock(InternalDelegationAssertionVerifier.class);
        when(delegationVerifier.isTrusted(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        provider = new AccountAdministrationProvider(workflow, receiptExecutor, delegationVerifier);
    }

    @Test
    void changeStateDelegatesWorkflowThroughReceiptBoundary() {
        ChangeAccountStateCommand command = stateCommand();
        RpcResult<AccountStateDTO> expected = RpcResult.success(
                new AccountStateDTO("user-1", false, false, 3L), "t-1");
        when(workflow.changeState(command)).thenReturn(expected);
        when(receiptExecutor.execute(
                eq("AccountAdministrationService"),
                eq("changeState"),
                same(command),
                eq(AccountStateDTO.class),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invokeMutation(invocation.getArgument(4)));

        RpcResult<AccountStateDTO> actual = provider.changeState(command);

        assertThat(actual).isSameAs(expected);
        verify(workflow).changeState(command);
        verify(receiptExecutor).execute(
                eq("AccountAdministrationService"),
                eq("changeState"),
                same(command),
                eq(AccountStateDTO.class),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void changeAuthorizationDelegatesWorkflowThroughReceiptBoundary() {
        ChangeAuthorizationCommand command = authorizationCommand();
        RpcResult<AuthorizationSnapshotDTO> expected = RpcResult.success(
                new AuthorizationSnapshotDTO(
                        "user-1", "ADMIN", Set.of("READ:PROBLEM"), 3L), "t-1");
        when(workflow.changeAuthorization(command)).thenReturn(expected);
        when(receiptExecutor.execute(
                eq("AccountAdministrationService"),
                eq("changeAuthorization"),
                same(command),
                eq(AuthorizationSnapshotDTO.class),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invokeMutation(invocation.getArgument(4)));

        RpcResult<AuthorizationSnapshotDTO> actual = provider.changeAuthorization(command);

        assertThat(actual).isSameAs(expected);
        verify(workflow).changeAuthorization(command);
        verify(receiptExecutor).execute(
                eq("AccountAdministrationService"),
                eq("changeAuthorization"),
                same(command),
                eq(AuthorizationSnapshotDTO.class),
                org.mockito.ArgumentMatchers.any());
    }

    @SuppressWarnings("unchecked")
    private static <T> RpcResult<T> invokeMutation(Object mutation) {
        return ((Function<String, RpcResult<T>>) mutation).apply("t-1");
    }

    private static ChangeAccountStateCommand stateCommand() {
        return new ChangeAccountStateCommand(
                "cmd-state",
                IdMetadata.mint(),
                new ActorDelegation("ADMIN", "admin-1", "org-1", "test"),
                new TraceMetadata("t-1", "span-1", null, null),
                "user-1",
                2L,
                ChangeAccountStateCommand.AccountStateAction.DISABLE,
                "test");
    }

    private static ChangeAuthorizationCommand authorizationCommand() {
        return new ChangeAuthorizationCommand(
                "cmd-auth",
                IdMetadata.mint(),
                new ActorDelegation("ADMIN", "admin-1", "org-1", "test"),
                new TraceMetadata("t-1", "span-1", null, null),
                "user-1",
                2L,
                "ADMIN",
                Set.of("READ:PROBLEM"),
                "test");
    }
}
