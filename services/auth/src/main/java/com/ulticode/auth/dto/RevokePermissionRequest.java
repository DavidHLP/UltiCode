package com.ulticode.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for
 * {@code DELETE /auth/admin/users/{id}/permissions}. P2-RBAC-001
 * owner-only command surface for revoking a direct
 * {@code user_permissions} row.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevokePermissionRequest {

    @NotBlank
    private String action;

    @NotBlank
    private String resource;
}
