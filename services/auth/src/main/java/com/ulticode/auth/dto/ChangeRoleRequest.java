package com.ulticode.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for
 * {@code POST /auth/admin/users/{id}/role}. P2-RBAC-001 owner-only
 * command surface for the {@code users.role} column. The role value
 * is uppercased and validated against an allow-list on the server
 * side; the regex here is a defensive belt-and-braces check.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRoleRequest {

    @NotBlank
    @Pattern(regexp = "^(USER|MODERATOR|ADMIN|SUPER_ADMIN)$",
            message = "role must be one of USER, MODERATOR, ADMIN, SUPER_ADMIN")
    private String role;
}
