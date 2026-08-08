package com.ulticode.auth.adapter.in.web;

import com.ulticode.auth.dto.ChangeRoleRequest;
import com.ulticode.auth.dto.GrantPermissionRequest;
import com.ulticode.auth.dto.RevokePermissionRequest;
import com.ulticode.auth.permission.service.RoleAdministrationService;
import com.ulticode.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * P2-RBAC-001 owner-only HTTP compatibility adapter for Auth-owned
 * {@code users.role} and {@code user_permissions} commands.
 *
 * <p>It delegates to the Auth-owned service and returns value objects; no
 * persistence entity or mapper crosses the web boundary. Cross-service
 * administration uses the provider-owned Dubbo contract.</p>
 */
@RestController
@RequestMapping("/auth/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class RoleAdministrationController {

    private final RoleAdministrationService roleAdministrationService;

    @PostMapping("/users/{id}/role")
    public Result<Map<String, String>> changeRole(@PathVariable("id") String userId,
                                                   @Valid @RequestBody ChangeRoleRequest body,
                                                   Principal principal) {
        final String actorId = principal == null ? "system" : principal.getName();
        final String applied = roleAdministrationService.changeRole(userId, body.getRole(), actorId);
        return Result.success(Map.of("userId", userId, "role", applied));
    }

    @PostMapping("/users/{id}/permissions")
    public Result<RoleAdministrationService.PermissionGrant> grantPermission(
            @PathVariable("id") String userId,
            @Valid @RequestBody GrantPermissionRequest body,
            Principal principal) {
        final String actorId = principal == null ? "system" : principal.getName();
        final RoleAdministrationService.PermissionGrant granted = roleAdministrationService.grantPermission(
                userId, body.getAction(), body.getResource(), body.getExpiresAt(), actorId);
        return Result.success(granted);
    }

    @DeleteMapping("/users/{id}/permissions")
    public Result<Map<String, Object>> revokePermission(@PathVariable("id") String userId,
                                                        @Valid @RequestBody RevokePermissionRequest body,
                                                        Principal principal) {
        final String actorId = principal == null ? "system" : principal.getName();
        final boolean removed = roleAdministrationService.revokePermission(
                userId, body.getAction(), body.getResource(), actorId);
        return Result.success(Map.of(
                "userId", userId,
                "action", body.getAction(),
                "resource", body.getResource(),
                "removed", removed));
    }
}
