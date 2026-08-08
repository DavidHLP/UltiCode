package com.ulticode.auth.api.dto;

import java.io.Serializable;

/**
 * Data carrier for password change requests.
 */
public record ChangePasswordDTO(
        String currentPassword,
        String newPassword,
        String confirmPassword) implements Serializable {

    public ChangePasswordDTO {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("currentPassword is required");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("newPassword is required");
        }
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new IllegalArgumentException("confirmPassword is required");
        }
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }
}
