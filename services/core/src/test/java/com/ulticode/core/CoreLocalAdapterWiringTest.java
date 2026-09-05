package com.ulticode.core;

import com.ulticode.admin.security.jwt.AccountReadAdapter;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.AuthorizationMutationDTO;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.AuthorizationMutationService;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.service.UserPermissionService;
import com.ulticode.modules.admin.service.impl.UserPermissionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class CoreLocalAdapterWiringTest {

    private static CoreOwnerContextManager manager() {
        return spy(new CoreOwnerContextManager(
                new CoreModuleRegistry(java.util.List.of()),
                new MockEnvironment(),
                false,
                1_000L));
    }

    @Test
    void adminConsumerUsesLocalIdentityContractAndMutationContract() {
        CoreOwnerContextManager ownerContexts = manager();
        IdentityQueryService authIdentity = mock(IdentityQueryService.class);
        doReturn(authIdentity).when(ownerContexts).bean("auth", IdentityQueryService.class);
        doReturn(RpcResult.success(
                new UserIdentityDTO("account-1", "alice", "ADMIN", true, false), "trace-1"))
                .when(authIdentity).getIdentity("account-1");

        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        try {
            ownerContexts.registerChildContracts(child, new CoreModuleDefinition(
                    "admin", "ADMIN", CoreOwnerBootConfigurations.Admin.class,
                    "adminTransactionManager", "backend-admin"));
            child.registerBean(AccountReadAdapter.class);
            child.refresh();

            assertThat(child.getBean(AuthorizationMutationService.class))
                    .isInstanceOf(CoreLocalAuthorizationMutationAdapter.class);
            assertThat(child.getBean(AccountQueryService.class))
                    .isInstanceOf(CoreLocalAccountQueryAdapter.class);
            assertThat(child.getBean(AccountReadAdapter.class).findById("account-1"))
                    .get()
                    .extracting(account -> account.id(), account -> account.username())
                    .containsExactly("account-1", "alice");
        } finally {
            child.close();
            ownerContexts.onContextClosed(new org.springframework.context.event.ContextClosedEvent(child));
        }
    }

    @Test
    void adminPermissionMutationSucceedsThroughLocalAccountQueryAndMutationSeams() {
        CoreOwnerContextManager ownerContexts = manager();
        AuthAccountDTO account =
                new AuthAccountDTO("user-123", "bob", "bob@example.com", "USER", true, false,
                        null, null, LocalDateTime.now().minusDays(30), null, 7L);

        // Auth child provider beans the local adapters delegate to.
        AccountQueryService authAccountQuery = mock(AccountQueryService.class);
        doReturn(authAccountQuery).when(ownerContexts).bean("auth", AccountQueryService.class);
        doReturn(RpcResult.success(account, "t-auth")).when(authAccountQuery).getAccountById("user-123");
        AuthorizationMutationService authMutation = mock(AuthorizationMutationService.class);
        doReturn(authMutation).when(ownerContexts).bean("auth", AuthorizationMutationService.class);
        doReturn(RpcResult.success(
                new AuthorizationMutationDTO("user-123", "GRANT", "READ", "PROBLEM",
                        "direct", null, 8L, true), "t-auth"))
                .when(authMutation).mutatePermission(any());

        // Signer for the mutation adapter; a real subject is not needed, the
        // assertion only has to be non-null for the adapter's delegation scope.
        com.ulticode.admin.security.DelegationAssertionSigner signer =
                mock(com.ulticode.admin.security.DelegationAssertionSigner.class);
        doReturn("signed-assertion").when(signer).issueForTarget("backend-auth");
        doReturn(signer).when(ownerContexts).bean("admin", com.ulticode.admin.security.DelegationAssertionSigner.class);

        // Authenticated SUPER_ADMIN actor so requireSuperAdminForManagePermissionsSystem passes
        // for MANAGE_PERMISSIONS:SYSTEM and the provider is non-null in the service.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin-1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));

        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        try {
            ownerContexts.registerChildContracts(child, new CoreModuleDefinition(
                    "admin", "ADMIN", CoreOwnerBootConfigurations.Admin.class,
                    "adminTransactionManager", "backend-admin"));
            // Register the real admin beans as container beans before refresh so
            // their @Autowired(required=false) @DubboReference fields autowire to
            // the local singletons registered by registerChildContracts. No
            // reflection: because the fields are optional, a missing local seam
            // would not fail refresh — it would stay null and the legal-grant
            // assertion below would fail with OWNER_QUERY_UNAVAILABLE. The
            // behavior assertion therefore detects missing registration.
            child.registerBean(com.ulticode.admin.security.SpringSecurityCurrentUserProvider.class);
            child.registerBean(UserPermissionServiceImpl.class);
            child.refresh();

            UserPermissionService service = child.getBean(UserPermissionService.class);
            AuthorizationMutationDTO result = service.assignUserPermission(
                    "user-123", "READ", "PROBLEM",
                    LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay());

            assertThat(result.accountId()).isEqualTo("user-123");
            assertThat(result.changed()).isTrue();
            assertThat(result.operation()).isEqualTo("GRANT");
        } finally {
            SecurityContextHolder.clearContext();
            child.close();
            ownerContexts.onContextClosed(new org.springframework.context.event.ContextClosedEvent(child));
        }
    }

    @Test
    void adminPermissionMutationStaysFailClosedWithoutAuthSignerOrProvider() {
        CoreOwnerContextManager ownerContexts = manager();
        AuthAccountDTO account =
                new AuthAccountDTO("user-123", "bob", "bob@example.com", "USER", true, false,
                        null, null, LocalDateTime.now().minusDays(30), null, 7L);
        AccountQueryService authAccountQuery = mock(AccountQueryService.class);
        doReturn(authAccountQuery).when(ownerContexts).bean("auth", AccountQueryService.class);
        doReturn(RpcResult.success(account, "t-auth")).when(authAccountQuery).getAccountById("user-123");
        // Delegation assertion signer missing → the local mutation adapter cannot
        // issue an assertion and must return UNAUTHORIZED rather than succeed.
        doReturn(null).when(ownerContexts).bean("admin", com.ulticode.admin.security.DelegationAssertionSigner.class);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin-1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));

        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        try {
            ownerContexts.registerChildContracts(child, new CoreModuleDefinition(
                    "admin", "ADMIN", CoreOwnerBootConfigurations.Admin.class,
                    "adminTransactionManager", "backend-admin"));
            child.registerBean(com.ulticode.admin.security.SpringSecurityCurrentUserProvider.class);
            child.registerBean(UserPermissionServiceImpl.class);
            child.refresh();

            UserPermissionService service = child.getBean(UserPermissionService.class);
            assertThatThrownBy(() -> service.assignUserPermission(
                    "user-123", "READ", "PROBLEM", null))
                    .satisfies(error -> assertThat(
                            ((com.ulticode.common.exception.BusinessException) error).getErrorCode())
                            .isEqualTo(com.ulticode.admin.error.AdminErrorCode.OWNER_QUERY_UNAVAILABLE));
        } finally {
            SecurityContextHolder.clearContext();
            child.close();
            ownerContexts.onContextClosed(new org.springframework.context.event.ContextClosedEvent(child));
        }
    }
}
