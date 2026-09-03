package com.ulticode.modules.admin.controller;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.websecurity.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.AdminCreateUserDTO;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.AdminUpdateUserDTO;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.BanUserRequest;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.BulkUserActionRequest;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.GrantPermissionRequest;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.ResetPasswordRequest;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.RevokePermissionRequest;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.query.AdminUserDetailQuery;
import com.ulticode.modules.admin.query.AdminUserDetailResult;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.service.UserManagementService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.service.UserPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Users", description = "用户管理接口")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminUserController {

    private final UserManagementService userManagementService;
    private final UserPermissionService userPermissionService;
    private final AdminUserDetailQuery adminUserDetailQuery;
    private final AdminUserProjection adminUserProjection;

    @Operation(summary = "Get users list", description = "Get paginated list of users with filters")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AdminUserVO>> getUsers(AdminUserQueryDTO query) {
        return Result.success(adminUserProjection.getUsers(query));
    }

    @Operation(summary = "Get user by ID", description = "Get detailed user information")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminUserVO> getUserById(@PathVariable String id) {
        return Result.success(userFromDetail(id));
    }

    @Operation(summary = "Create user", description = "Create a new user account")
    @RateLimit(key = "admin:user-create", limit = 30, period = 60)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminUserVO> createUser(@Valid @RequestBody AdminCreateUserDTO dto) {
        return Result.success(userManagementService.createUser(dto));
    }

    @Operation(summary = "Update user", description = "Update user information")
    @RateLimit(key = "admin:user-update", limit = 30, period = 60)
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminUserVO> updateUser(
            @PathVariable String id,
            @Valid @RequestBody AdminUpdateUserDTO dto) {
        return Result.success(userManagementService.updateUser(id, dto));
    }

    @Operation(summary = "Delete user", description = "Delete a user account")
    @RateLimit(key = "admin:user-delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public Result<Void> deleteUser(@PathVariable String id) {
        userManagementService.deleteUser(id);
        return Result.success();
    }

    @Operation(summary = "Ban user", description = "Ban a user from the platform")
    @RateLimit(key = "admin:user-ban", limit = 30, period = 60)
    @PostMapping("/{id}/ban")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminUserVO> banUser(
            @PathVariable String id,
            @RequestBody(required = false) BanUserRequest request) {
        String reason = request != null ? request.getReason() : null;
        String until = request != null ? request.getUntil() : null;
        return Result.success(userManagementService.banUser(id, reason, until));
    }

    @Operation(summary = "Unban user", description = "Remove ban from a user")
    @RateLimit(key = "admin:user-unban", limit = 30, period = 60)
    @PostMapping("/{id}/unban")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminUserVO> unbanUser(@PathVariable String id) {
        return Result.success(userManagementService.unbanUser(id));
    }

    @Operation(summary = "Reset user password", description = "Reset a user's password")
    @RateLimit(key = "admin:user-reset-password", limit = 30, period = 60)
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> resetPassword(
            @PathVariable String id,
            @Valid @RequestBody ResetPasswordRequest request) {
        userManagementService.resetPassword(id, request.getPassword());
        return Result.success();
    }

    @Operation(summary = "Grant direct permission to a user",
               description = "Assigns an action:resource permission directly to a user, " +
                             "independent of their role. Idempotent: existing permission is updated.")
    @RateLimit(key = "admin:user-permission-grant", limit = 30, period = 60)
    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminUserVO> grantUserPermission(
            @PathVariable String id,
            @Valid @RequestBody GrantPermissionRequest request) {
        return Result.success(userPermissionService.assignUserPermission(
            id, request.getAction(), request.getResource(), request.getExpiresAt()));
    }

    @Operation(summary = "Revoke direct permission from a user",
               description = "Removes a previously granted action:resource permission. " +
                             "Returns 200 with updated VO even if the permission did not exist. " +
                             "Supports both query string (?action=X&resource=Y) and request body " +
                             "(Spring proxies / some curl wrappers drop DELETE bodies). " +
                             "Query params take precedence when both are provided.")
    @RateLimit(key = "admin:user-permission-revoke", limit = 30, period = 60)
    @DeleteMapping("/{id}/permissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminUserVO> revokeUserPermission(
            @PathVariable String id,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resource,
            @RequestBody(required = false) RevokePermissionRequest request) {
        String act = action;
        String res = resource;
        if ((act == null || res == null) && request != null) {
            // body 优先级低于 query param(若两者都给,以 query 为准)
            if (act == null) {
                act = request.getAction();
            }
            if (res == null) {
                res = request.getResource();
            }
        }
        if (!org.springframework.util.StringUtils.hasText(act)
                || !org.springframework.util.StringUtils.hasText(res)) {
            throw new BusinessException(
                AdminErrorCode.VALIDATION_FAILED,
                "action and resource are required (via query string or request body)");
        }
        return Result.success(userPermissionService.revokeUserPermission(id, act, res));
    }

    @Operation(summary = "Bulk ban users", description = "Ban multiple users at once")
    @RateLimit(key = "admin:user-bulk-ban", limit = 30, period = 60)
    @PostMapping("/bulk-ban")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<UserManagementService.BanResult>> bulkBan(@Valid @RequestBody BulkUserActionRequest request) {
        return Result.success(userManagementService.bulkBan(request.getIds(), request.getReason()));
    }

    @Operation(summary = "Bulk unban users", description = "Unban multiple users at once")
    @RateLimit(key = "admin:user-bulk-unban", limit = 30, period = 60)
    @PostMapping("/bulk-unban")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<UserManagementService.BanResult>> bulkUnban(@Valid @RequestBody BulkUserActionRequest request) {
        return Result.success(userManagementService.bulkUnban(request.getIds()));
    }

    @Operation(summary = "Bulk delete users", description = "Delete multiple users at once")
    @RateLimit(key = "admin:user-bulk-delete", limit = 30, period = 60)
    @DeleteMapping("/bulk-delete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public Result<List<UserManagementService.DeleteResult>> bulkDelete(@Valid @RequestBody BulkUserActionRequest request) {
        return Result.success(userManagementService.bulkDelete(request.getIds()));
    }
    private AdminUserVO userFromDetail(String id) {
        AdminUserDetailResult result = adminUserDetailQuery.loadUserDetail(id);
        if (result == null || result.failure() == AdminUserDetailResult.Failure.NOT_FOUND) {
            throw new BusinessException(AdminErrorCode.USER_NOT_FOUND);
        }
        if (result.failure() == AdminUserDetailResult.Failure.TRANSPORT_UNAVAILABLE
                || result.user() == null) {
            throw new BusinessException(
                    AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                    "Admin user detail query unavailable");
        }
        return result.user();
    }
}
