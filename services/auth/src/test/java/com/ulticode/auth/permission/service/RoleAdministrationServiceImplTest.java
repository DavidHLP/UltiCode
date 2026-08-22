package com.ulticode.auth.permission.service;

import com.ulticode.auth.account.entity.AuthAccountEntity;
import com.ulticode.auth.account.mapper.AuthAccountMapper;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.mapper.UserRoleMapper;
import com.ulticode.auth.permission.port.UserRoleWritePort;
import com.ulticode.auth.permission.service.impl.RoleAdministrationServiceImpl;
import com.ulticode.common.audit.AuditSinkPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAdministrationServiceImplTest {

    @Mock
    private UserRoleWritePort userRoleWritePort;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private PermissionService permissionService;

    @Mock
    private AuthAccountMapper authAccountMapper;

    @Mock
    private AuditSinkPort auditSinkPort;

    private RoleAdministrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoleAdministrationServiceImpl(
                userRoleWritePort, userRoleMapper, permissionService,
                authAccountMapper, auditSinkPort);
    }

    @Test
    void roleChangeRecordsDurableAuthorizationEventWithVersion() {
        when(userRoleMapper.existsById("user-1")).thenReturn(1);
        when(authAccountMapper.findById("user-1"))
                .thenReturn(account(5L), account(6L));
        when(userRoleWritePort.changeRole("user-1", "ADMIN")).thenReturn("ADMIN");

        assertThat(service.changeRole("user-1", "ADMIN", "admin-1")).isEqualTo("ADMIN");

        Map<String, Object> payload = verifyEvent("admin-1", "user-1");
        assertThat(payload).containsEntry("change", "ROLE")
                .containsEntry("role", "ADMIN")
                .containsEntry("authzVersion", 6L);
    }

    @Test
    void permissionGrantBumpsVersionAndRecordsDurableEvent() {
        UserPermission permission = new UserPermission();
        permission.setId("grant-1");
        permission.setUserId("user-1");
        permission.setAction("READ");
        permission.setResource("PROBLEM");
        when(userRoleMapper.existsById("user-1")).thenReturn(1);
        when(permissionService.assignPermission("user-1", "READ", "PROBLEM", null))
                .thenReturn(permission);
        when(authAccountMapper.bumpAuthzVersion("user-1")).thenReturn(1);
        when(authAccountMapper.findById("user-1")).thenReturn(account(7L));

        service.grantPermission("user-1", "READ", "PROBLEM", null, "admin-1");

        verify(authAccountMapper).bumpAuthzVersion("user-1");
        Map<String, Object> payload = verifyEvent("admin-1", "user-1");
        assertThat(payload).containsEntry("change", "PERMISSION_GRANTED")
                .containsEntry("permissionId", "grant-1")
                .containsEntry("authzVersion", 7L);
    }

    @Test
    void permissionRevokeBumpsVersionOnlyWhenPermissionWasRemoved() {
        when(userRoleMapper.existsById("user-1")).thenReturn(1);
        when(permissionService.revokePermission("user-1", "READ", "PROBLEM")).thenReturn(true);
        when(authAccountMapper.bumpAuthzVersion("user-1")).thenReturn(1);
        when(authAccountMapper.findById("user-1")).thenReturn(account(8L));

        assertThat(service.revokePermission("user-1", "READ", "PROBLEM", "admin-1")).isTrue();

        verify(authAccountMapper).bumpAuthzVersion("user-1");
        Map<String, Object> payload = verifyEvent("admin-1", "user-1");
        assertThat(payload).containsEntry("change", "PERMISSION_REVOKED")
                .containsEntry("authzVersion", 8L);
    }

    @Test
    void sameRoleDoesNotEmitAuthorizationEvent() {
        when(userRoleMapper.existsById("user-1")).thenReturn(1);
        when(authAccountMapper.findById("user-1"))
                .thenReturn(account(5L), account(5L));
        when(userRoleWritePort.changeRole("user-1", "USER")).thenReturn("USER");

        service.changeRole("user-1", "USER", "admin-1");

        verify(auditSinkPort, never()).log(any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    private Map<String, Object> verifyEvent(String actorId, String userId) {
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(auditSinkPort).log(
                eq(actorId), eq(userId), eq("AUTHORIZATION_CHANGED"),
                eq("USER_AUTHORIZATION"), eq(userId), isNull(), payload.capture(),
                eq("unknown"), isNull());
        return payload.getValue();
    }

    private static AuthAccountEntity account(long version) {
        AuthAccountEntity account = new AuthAccountEntity();
        account.setId("user-1");
        account.setRole("USER");
        account.setAuthzVersion(version);
        return account;
    }
}
