package com.ulticode.auth.adapter.in.web;

import com.ulticode.auth.dto.ChangeRoleRequest;
import com.ulticode.auth.dto.GrantPermissionRequest;
import com.ulticode.auth.dto.RevokePermissionRequest;
import com.ulticode.auth.permission.entity.UserPermission;
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
 * P2-RBAC-001 owner-only HTTP command surface for
 * {@code users.role} and {@code user_permissions} writes.
 *
 * <p>Privileged; requires {@code ADMIN} or {@code SUPER_ADMIN} via
 * {@link PreAuthorize}. The Dubbo provider port
 * ({@code AccountAdministrationService}) will replace the HTTP
 * surface in P4-RPC-001 once Phase 4 begins; the implementation
 * service is the same {@link RoleAdministrationService}, so the
 * HTTP-to-Dubbo migration is a controller swap, not a logic
 * rewrite.
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
    public Result<UserPermission> grantPermission(@PathVariable("id") String userId,
                                                  @Valid @RequestBody GrantPermissionRequest body,
                                                  Principal principal) {
        final String actorId = principal == null ? "system" : principal.getName();
        final UserPermission granted = roleAdministrationService.grantPermission(
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
