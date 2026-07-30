package com.ulticode.modules.auth.service;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.auth.api.service.AuthorizationSnapshotService;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.admin.client.BackendAuthRoleAdminClient;
import com.ulticode.modules.auth.account.DefaultAuthAccountAdapter;
import com.ulticode.modules.permission.service.PermissionService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthCutoverServiceTest {

    private UserMapper userMapper;
    private DefaultAuthAccountAdapter defaultAuthAccountAdapter;
    private PermissionService permissionService;
    private BackendAuthRoleAdminClient backendAuthRoleAdminClient;
    private IdentityQueryService identityQueryService;
    private AuthorizationSnapshotService authorizationSnapshotService;
    private AccountAdministrationService accountAdministrationService;

    private AuthCutoverService cutoverService;

    private ActorDelegation actor;
    private TraceMetadata trace;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        defaultAuthAccountAdapter = mock(DefaultAuthAccountAdapter.class);
        permissionService = mock(PermissionService.class);
        backendAuthRoleAdminClient = mock(BackendAuthRoleAdminClient.class);
        identityQueryService = mock(IdentityQueryService.class);
        authorizationSnapshotService = mock(AuthorizationSnapshotService.class);
        accountAdministrationService = mock(AccountAdministrationService.class);

        cutoverService = new AuthCutoverService(userMapper, defaultAuthAccountAdapter, permissionService, backendAuthRoleAdminClient);

        ReflectionTestUtils.setField(cutoverService, "identityQueryService", identityQueryService);
        ReflectionTestUtils.setField(cutoverService, "authorizationSnapshotService", authorizationSnapshotService);
        ReflectionTestUtils.setField(cutoverService, "accountAdministrationService", accountAdministrationService);

        actor = new ActorDelegation("ADMIN", "admin-1", "org-1", "reason");
        trace = TraceMetadata.EMPTY;
    }

    @Test
    @DisplayName("getIdentity delegates to local UserMapper when dubboEnabled is false")
    void getIdentityLocalPath() {
        ReflectionTestUtils.setField(cutoverService, "dubboEnabled", false);

        User user = new User();
        user.setId("user-1");
        user.setUsername("alice");
        user.setRole("USER");
        user.setIsActive(true);
        user.setIsBanned(false);
        user.setIsDeleted(0);
        when(userMapper.selectById("user-1")).thenReturn(user);

        UserIdentityDTO identity = cutoverService.getIdentity("user-1");

        assertThat(identity.accountId()).isEqualTo("user-1");
        assertThat(identity.username()).isEqualTo("alice");
        verify(identityQueryService, never()).getIdentity(anyString());
    }

    @Test
    @DisplayName("getSnapshot delegates to local UserMapper & PermissionService when dubboEnabled is false")
    void getSnapshotLocalPath() {
        ReflectionTestUtils.setField(cutoverService, "dubboEnabled", false);

        User user = new User();
        user.setId("user-1");
        user.setRole("ADMIN");
        user.setIsDeleted(0);
        when(userMapper.selectById("user-1")).thenReturn(user);
        when(permissionService.getUserPermissionStrings("user-1")).thenReturn(List.of("READ:PROBLEM", "WRITE:PROBLEM"));

        AuthorizationSnapshotDTO snapshot = cutoverService.getSnapshot("user-1");

        assertThat(snapshot.accountId()).isEqualTo("user-1");
        assertThat(snapshot.role()).isEqualTo("ADMIN");
        assertThat(snapshot.permissions()).containsExactlyInAnyOrder("READ:PROBLEM", "WRITE:PROBLEM");
        verify(authorizationSnapshotService, never()).getSnapshot(anyString());
    }

    @Test
    @DisplayName("changeState BAN delegates to defaultAuthAccountAdapter when dubboEnabled is false")
    void changeStateBanLocalPath() {
        ReflectionTestUtils.setField(cutoverService, "dubboEnabled", false);

        User user = new User();
        user.setId("user-1");
        user.setIsActive(true);
        user.setIsBanned(false);
        user.setIsDeleted(0);
        when(userMapper.selectById("user-1")).thenReturn(user);

        ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                "cmd-1", IdMetadata.mint(), actor, trace, "user-1", 0L,
                ChangeAccountStateCommand.AccountStateAction.BAN, "ban user"
        );

        AccountStateDTO state = cutoverService.changeState(command);

        assertThat(state.accountId()).isEqualTo("user-1");
        assertThat(state.banned()).isTrue();
        verify(defaultAuthAccountAdapter).updateBanStatus("user-1", true, "ban user");
    }

    @Test
    @DisplayName("changeState DISABLE updates user.isActive via defaultAuthAccountAdapter when dubboEnabled is false")
    void changeStateDisableLocalPath() {
        ReflectionTestUtils.setField(cutoverService, "dubboEnabled", false);

        User user = new User();
        user.setId("user-1");
        user.setIsActive(true);
        user.setIsBanned(false);
        user.setIsDeleted(0);
        when(userMapper.selectById("user-1")).thenReturn(user);

        ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                "cmd-1", IdMetadata.mint(), actor, trace, "user-1", 0L,
                ChangeAccountStateCommand.AccountStateAction.DISABLE, "disable user"
        );

        AccountStateDTO state = cutoverService.changeState(command);

        assertThat(state.accountId()).isEqualTo("user-1");
        assertThat(state.active()).isFalse();
        verify(defaultAuthAccountAdapter).updateActiveStatus("user-1", false);
    }

    @Test
    @DisplayName("changeAuthorization updates role via defaultAuthAccountAdapter when dubboEnabled is false")
    void changeAuthorizationLocalPath() {
        ReflectionTestUtils.setField(cutoverService, "dubboEnabled", false);

        User user = new User();
        user.setId("user-1");
        user.setRole("USER");
        user.setIsDeleted(0);
        when(userMapper.selectById("user-1")).thenReturn(user);
        when(permissionService.getUserPermissionStrings("user-1")).thenReturn(List.of("READ:PROBLEM"));

        ChangeAuthorizationCommand command = new ChangeAuthorizationCommand(
                "cmd-2", IdMetadata.mint(), actor, trace, "user-1", 0L,
                "ADMIN", Set.of("READ:PROBLEM"), "grant admin"
        );

        AuthorizationSnapshotDTO snapshot = cutoverService.changeAuthorization(command);

        assertThat(snapshot.accountId()).isEqualTo("user-1");
        assertThat(snapshot.role()).isEqualTo("ADMIN");
        verify(defaultAuthAccountAdapter).updateAccountCredentials("user-1", null, null, "ADMIN");
    }
}
