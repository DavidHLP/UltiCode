package com.ulticode.auth.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request body for
 * {@code POST /auth/admin/users/{id}/permissions}. P2-RBAC-001
 * owner-only command surface for the {@code user_permissions}
 * table. {@code expiresAt} is optional; null means "no expiry".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrantPermissionRequest {

    @NotBlank
    private String action;

    @NotBlank
    private String resource;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;
}
