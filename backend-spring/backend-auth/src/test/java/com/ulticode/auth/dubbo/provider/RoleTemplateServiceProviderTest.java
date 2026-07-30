package com.ulticode.auth.dubbo.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.auth.api.dto.PermissionEntry;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.permission.entity.RolePermission;
import com.ulticode.auth.permission.mapper.RolePermissionMapper;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoleTemplateServiceProviderTest {

    private RolePermissionMapper rolePermissionMapper;
    private RoleTemplateServiceProvider provider;

    @BeforeEach
    void setUp() {
        rolePermissionMapper = mock(RolePermissionMapper.class);
        provider = new RoleTemplateServiceProvider(rolePermissionMapper);
    }

    @Test
    @DisplayName("getRoleTemplate returns role entries with source=role and null expiresAt")
    void getRoleTemplateSuccess() {
        RolePermission rp1 = new RolePermission();
        rp1.setRole("ADMIN");
        rp1.setAction("MANAGE_USERS");
        rp1.setResource("USER");

        RolePermission rp2 = new RolePermission();
        rp2.setRole("ADMIN");
        rp2.setAction("DELETE");
        rp2.setResource("PROBLEM");

        when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rp1, rp2));

        RpcResult<List<PermissionEntry>> result = provider.getRoleTemplate("ADMIN");

        assertThat(result.success()).isTrue();
        assertThat(result.data()).hasSize(2);
        assertThat(result.data()).allSatisfy(e -> {
            assertThat(e.source()).isEqualTo("role");
            assertThat(e.expiresAt()).isNull();
        });
        assertThat(result.data())
                .extracting(PermissionEntry::action)
                .containsExactlyInAnyOrder("MANAGE_USERS", "DELETE");
    }

    @Test
    @DisplayName("getRoleTemplate normalizes lowercase role to uppercase")
    void getRoleTemplateNormalizesCase() {
        when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());

        RpcResult<List<PermissionEntry>> result = provider.getRoleTemplate("admin");

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEmpty();
    }

    @Test
    @DisplayName("getRoleTemplate returns empty list for role with zero template permissions")
    void getRoleTemplateEmptyPermissions() {
        when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());

        RpcResult<List<PermissionEntry>> result = provider.getRoleTemplate("USER");

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEmpty();
    }

    @Test
    @DisplayName("getRoleTemplate returns ROLE_NOT_FOUND for unknown role")
    void getRoleTemplateUnknownRole() {
        RpcResult<List<PermissionEntry>> result = provider.getRoleTemplate("SUPERUSER");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.ROLE_NOT_FOUND.code());
    }

    @Test
    @DisplayName("getRoleTemplate returns ROLE_NOT_FOUND for null or blank role")
    void getRoleTemplateNullRole() {
        RpcResult<List<PermissionEntry>> result = provider.getRoleTemplate(null);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.ROLE_NOT_FOUND.code());

        result = provider.getRoleTemplate("  ");
        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.ROLE_NOT_FOUND.code());
    }
}
