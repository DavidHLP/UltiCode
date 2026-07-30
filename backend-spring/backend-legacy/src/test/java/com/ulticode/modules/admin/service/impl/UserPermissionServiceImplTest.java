package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.client.BackendAuthRoleAdminClient;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.service.UserPermissionService;
import com.ulticode.modules.auth.service.AuthCutoverService;
import com.ulticode.modules.permission.PermissionVocabulary;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UserPermissionServiceImpl")
class UserPermissionServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private BackendAuthRoleAdminClient backendAuthRoleAdminClient;
    @Mock
    private AdminUserProjection adminUserProjection;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private Clock clock;
    private UserPermissionServiceImpl userPermissionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        clock = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("UTC"));

        lenient().when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(true);
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn("test-super-admin");
        AuthCutoverService authCutoverService = mock(AuthCutoverService.class);
        userPermissionService = new UserPermissionServiceImpl(
                userMapper, backendAuthRoleAdminClient, authCutoverService, adminUserProjection, clock,
                new PermissionVocabulary(), currentUserProvider);
    }

    private User createValidUser() {
        User user = new User();
        user.setId("user-123");
        user.setUsername("testuser");
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setRole("USER");
        user.setIsActive(true);
        user.setIsBanned(false);
        return user;
    }

    @Test
    @DisplayName("assignUserPermission succeeds for non-super-admin permission")
    void assignPermissionSuccess() {
        User user = createValidUser();
        when(userMapper.selectById("user-123")).thenReturn(user);

        AdminUserVO vo = new AdminUserVO();
        vo.setId("user-123");
        when(adminUserProjection.getUserById("user-123")).thenReturn(vo);

        AdminUserVO result = userPermissionService.assignUserPermission(
                "user-123", "READ", "PROBLEM", null);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("user-123");
    }

    @Test
    @DisplayName("revokeUserPermission succeeds")
    void revokePermissionSuccess() {
        User user = createValidUser();
        when(userMapper.selectById("user-123")).thenReturn(user);

        AdminUserVO vo = new AdminUserVO();
        vo.setId("user-123");
        when(adminUserProjection.getUserById("user-123")).thenReturn(vo);

        AdminUserVO result = userPermissionService.revokeUserPermission(
                "user-123", "READ", "PROBLEM");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("user-123");
    }
}
