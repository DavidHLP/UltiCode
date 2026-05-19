package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.AuditHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.permission.entity.RolePermission;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.mapper.RolePermissionMapper;
import com.ulticode.modules.permission.service.PermissionService;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserServiceImpl")
class AdminUserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditHelper auditHelper;

    @Mock
    private SubmissionMapper submissionMapper;

    @Mock
    private SolutionMapper solutionMapper;

    @Mock
    private PermissionService permissionService;

    @Mock
    private RolePermissionMapper rolePermissionMapper;

    private AdminUserServiceImpl adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserServiceImpl(
                userMapper, passwordEncoder, auditHelper,
                submissionMapper, solutionMapper, permissionService, rolePermissionMapper);
    }

    private User createValidUser() {
        User user = new User();
        user.setId("user-123");
        user.setUsername("testuser");
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setRole("ADMIN");
        user.setIsActive(true);
        user.setIsBanned(false);
        return user;
    }

    @Nested
    @DisplayName("getUsers()")
    class GetUsers {

        @Test
        @DisplayName("does not trigger stats or permission queries for list view")
        void doesNotTriggerExtraQueries() {
            User user = createValidUser();
            Page<User> page = new Page<>();
            page.setRecords(List.of(user));
            page.setTotal(1);
            when(userMapper.selectPage(any(Page.class), any())).thenReturn(page);

            adminUserService.getUsers(new AdminUserQueryDTO());

            verifyNoInteractions(submissionMapper, solutionMapper, rolePermissionMapper, permissionService);
        }
    }

    @Nested
    @DisplayName("getUserById()")
    class GetUserById {

        @Test
        @DisplayName("populates stats correctly when mapper returns values")
        void populatesStatsCorrectly() {
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            when(submissionMapper.countByUserId("user-123")).thenReturn(10L);
            when(submissionMapper.countAcceptedProblemsByUserId("user-123")).thenReturn(5L);
            when(solutionMapper.countByUserId("user-123")).thenReturn(3L);
            when(submissionMapper.calculateStreak("user-123")).thenReturn(7);
            when(rolePermissionMapper.selectList(any())).thenReturn(List.of());
            when(permissionService.getUserPermissions("user-123")).thenReturn(List.of());

            AdminUserVO result = adminUserService.getUserById("user-123");

            assertThat(result).isNotNull();
            assertThat(result.getStats()).isNotNull();
            assertThat(result.getStats().getTotalSubmissions()).isEqualTo(10);
            assertThat(result.getStats().getAcceptedSubmissions()).isEqualTo(5);
            assertThat(result.getStats().getTotalSolutions()).isEqualTo(3);
            assertThat(result.getStats().getStreak()).isEqualTo(7);
        }

        @Test
        @DisplayName("defaults stats to zero when mappers return null")
        void nullMapperReturns_defaultsToZero() {
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            when(submissionMapper.countByUserId("user-123")).thenReturn(null);
            when(submissionMapper.countAcceptedProblemsByUserId("user-123")).thenReturn(null);
            when(solutionMapper.countByUserId("user-123")).thenReturn(null);
            when(submissionMapper.calculateStreak("user-123")).thenReturn(null);
            when(rolePermissionMapper.selectList(any())).thenReturn(List.of());
            when(permissionService.getUserPermissions("user-123")).thenReturn(List.of());

            AdminUserVO result = adminUserService.getUserById("user-123");

            assertThat(result).isNotNull();
            assertThat(result.getStats()).isNotNull();
            assertThat(result.getStats().getTotalSubmissions()).isEqualTo(0);
            assertThat(result.getStats().getAcceptedSubmissions()).isEqualTo(0);
            assertThat(result.getStats().getTotalSolutions()).isEqualTo(0);
            assertThat(result.getStats().getStreak()).isEqualTo(0);
        }

        @Test
        @DisplayName("populates permissions with role and direct permissions")
        void populatesPermissionsCorrectly() {
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            when(submissionMapper.countByUserId("user-123")).thenReturn(0L);
            when(submissionMapper.countAcceptedProblemsByUserId("user-123")).thenReturn(0L);
            when(solutionMapper.countByUserId("user-123")).thenReturn(0L);
            when(submissionMapper.calculateStreak("user-123")).thenReturn(0);

            RolePermission rolePerm = new RolePermission();
            rolePerm.setAction("read");
            rolePerm.setResource("users");
            when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rolePerm));

            UserPermission directPerm = new UserPermission();
            directPerm.setAction("write");
            directPerm.setResource("problems");
            when(permissionService.getUserPermissions("user-123")).thenReturn(List.of(directPerm));

            AdminUserVO result = adminUserService.getUserById("user-123");

            assertThat(result).isNotNull();
            assertThat(result.getPermissions()).hasSize(2);
            assertThat(result.getPermissions().get(0).getSource()).isEqualTo("role");
            assertThat(result.getPermissions().get(0).getAction()).isEqualTo("read");
            assertThat(result.getPermissions().get(1).getSource()).isEqualTo("direct");
            assertThat(result.getPermissions().get(1).getAction()).isEqualTo("write");
        }

        @Test
        @DisplayName("throws BusinessException when user not found")
        void userNotFound_throwsBusinessException() {
            when(userMapper.selectById("nonexistent")).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.getUserById("nonexistent"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                    });
        }
    }
}
