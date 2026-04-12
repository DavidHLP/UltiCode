package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.dto.BanUserRequest;
import com.ulticode.modules.admin.dto.BulkUserActionRequest;
import com.ulticode.modules.admin.dto.ResetPasswordRequest;
import com.ulticode.modules.admin.service.AdminUserService;
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

    private final AdminUserService adminUserService;

    @Operation(summary = "Get users list", description = "Get paginated list of users with filters")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AdminUserVO>> getUsers(AdminUserQueryDTO query) {
        return Result.success(adminUserService.getUsers(query));
    }

    @Operation(summary = "Get user by ID", description = "Get detailed user information")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminUserVO> getUserById(@PathVariable String id) {
        return Result.success(adminUserService.getUserById(id));
    }

    @Operation(summary = "Ban user", description = "Ban a user from the platform")
    @PostMapping("/{id}/ban")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminUserVO> banUser(
            @PathVariable String id,
            @RequestBody(required = false) BanUserRequest request) {
        String reason = request != null ? request.getReason() : null;
        String until = request != null ? request.getUntil() : null;
        return Result.success(adminUserService.banUser(id, reason, until));
    }

    @Operation(summary = "Unban user", description = "Remove ban from a user")
    @PostMapping("/{id}/unban")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminUserVO> unbanUser(@PathVariable String id) {
        return Result.success(adminUserService.unbanUser(id));
    }

    @Operation(summary = "Reset user password", description = "Reset a user's password")
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> resetPassword(
            @PathVariable String id,
            @Valid @RequestBody ResetPasswordRequest request) {
        adminUserService.resetPassword(id, request.getPassword());
        return Result.success();
    }

    @Operation(summary = "Bulk ban users", description = "Ban multiple users at once")
    @PostMapping("/bulk-ban")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<AdminUserService.BanResult>> bulkBan(@Valid @RequestBody BulkUserActionRequest request) {
        return Result.success(adminUserService.bulkBan(request.getIds(), request.getReason()));
    }

    @Operation(summary = "Bulk unban users", description = "Unban multiple users at once")
    @PostMapping("/bulk-unban")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<AdminUserService.BanResult>> bulkUnban(@Valid @RequestBody BulkUserActionRequest request) {
        return Result.success(adminUserService.bulkUnban(request.getIds()));
    }

    @Operation(summary = "Bulk delete users", description = "Delete multiple users at once")
    @DeleteMapping("/bulk-delete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public Result<List<AdminUserService.DeleteResult>> bulkDelete(@Valid @RequestBody BulkUserActionRequest request) {
        return Result.success(adminUserService.bulkDelete(request.getIds()));
    }
}
