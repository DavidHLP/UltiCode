package com.ulticode.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Reset password request DTO.
 */
@Data
public class ResetPasswordDTO {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 255, message = "Password must be between 8 and 255 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[\\S]+$",
            message = "Password must contain at least one lowercase letter, one uppercase letter, one digit, and no whitespace")
    private String newPassword;
}
