package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin controller for user management
 */
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
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        String until = body != null ? body.get("until") : null;
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
            @RequestBody Map<String, String> body) {
        String newPassword = body.get("password");
        adminUserService.resetPassword(id, newPassword);
        return Result.success();
    }

    @Operation(summary = "Bulk ban users", description = "Ban multiple users at once")
    @PostMapping("/bulk-ban")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<AdminUserService.BanResult>> bulkBan(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        String reason = (String) body.get("reason");
        return Result.success(adminUserService.bulkBan(ids, reason));
    }

    @Operation(summary = "Bulk unban users", description = "Unban multiple users at once")
    @PostMapping("/bulk-unban")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<AdminUserService.BanResult>> bulkUnban(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        return Result.success(adminUserService.bulkUnban(ids));
    }

    @Operation(summary = "Bulk delete users", description = "Delete multiple users at once")
    @DeleteMapping("/bulk-delete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public Result<List<AdminUserService.DeleteResult>> bulkDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        return Result.success(adminUserService.bulkDelete(ids));
    }
}
