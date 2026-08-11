package com.ulticode.app.security;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DubboIdentityActorAuthorizerTest {

    private final IdentityQueryService identityQueryService = mock(IdentityQueryService.class);
    private final InternalDelegationAssertionVerifier delegationVerifier =
            mock(InternalDelegationAssertionVerifier.class);
    private final DubboIdentityActorAuthorizer authorizer =
            new DubboIdentityActorAuthorizer(identityQueryService, delegationVerifier);

    @BeforeEach
    void trustTestCaller() {
        when(delegationVerifier.isTrusted(any())).thenReturn(true);
    }
    @Test
    void acceptsActiveUnbannedAdminWithMatchingRole() {
        ActorDelegation actor = actor("ADMIN", "admin-1");
        when(identityQueryService.getIdentity("admin-1"))
                .thenReturn(RpcResult.success(identity("admin-1", "ADMIN", true, false), "trace-1"));

        assertThat(authorizer.isAuthorized(actor)).isTrue();
    }

    @Test
    void acceptsSuperAdminWithMatchingRole() {
        ActorDelegation actor = actor("SUPER_ADMIN", "admin-1");
        when(identityQueryService.getIdentity("admin-1"))
                .thenReturn(RpcResult.success(identity("admin-1", "SUPER_ADMIN", true, false), "trace-1"));

        assertThat(authorizer.isAuthorized(actor)).isTrue();
    }

    @Test
    void rejectsInactiveBannedOrMismatchedIdentity() {
        ActorDelegation actor = actor("ADMIN", "admin-1");
        when(identityQueryService.getIdentity("admin-1"))
                .thenReturn(RpcResult.success(identity("admin-1", "ADMIN", false, false), "trace-1"));
        assertThat(authorizer.isAuthorized(actor)).isFalse();

        when(identityQueryService.getIdentity("admin-1"))
                .thenReturn(RpcResult.success(identity("admin-1", "ADMIN", true, true), "trace-1"));
        assertThat(authorizer.isAuthorized(actor)).isFalse();

        when(identityQueryService.getIdentity("admin-1"))
                .thenReturn(RpcResult.success(identity("admin-1", "USER", true, false), "trace-1"));
        assertThat(authorizer.isAuthorized(actor)).isFalse();
    }

    @Test
    void rejectsFailedOrIncompleteAuthResponse() {
        ActorDelegation actor = actor("ADMIN", "admin-1");
        when(identityQueryService.getIdentity("admin-1")).thenReturn(null);
        assertThat(authorizer.isAuthorized(actor)).isFalse();

        when(identityQueryService.getIdentity("admin-1"))
                .thenReturn(RpcResult.failure(com.ulticode.auth.api.error.AuthErrorCode.ACCOUNT_NOT_FOUND, "trace-1"));
        assertThat(authorizer.isAuthorized(actor)).isFalse();

        when(identityQueryService.getIdentity("admin-1"))
                .thenThrow(new IllegalStateException("auth unavailable"));
        assertThat(authorizer.isAuthorized(actor)).isFalse();
    }

    @Test
    void rejectsMalformedActorBeforeAuthLookup() {
        assertThat(authorizer.isAuthorized(null)).isFalse();
        assertThat(authorizer.isAuthorized(actor("ADMIN", ""))).isFalse();
        assertThat(authorizer.isAuthorized(actor("USER", "admin-1"))).isFalse();
    }

    private static ActorDelegation actor(String type, String actorId) {
        return new ActorDelegation(type, actorId, "delegator-1", "test");
    }

    private static UserIdentityDTO identity(String accountId, String role, boolean active, boolean banned) {
        return new UserIdentityDTO(accountId, "admin", role, active, banned);
    }
}
