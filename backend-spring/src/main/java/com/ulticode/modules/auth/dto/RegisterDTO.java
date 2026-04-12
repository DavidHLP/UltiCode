package com.ulticode.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Registration request DTO.
 */
@Data
public class RegisterDTO {

    /**
     * Unique username (3-120 characters)
     */
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 120, message = "Username must be between 3 and 120 characters")
    private String username;

    /**
     * Password (8-255 characters, must contain uppercase, lowercase, and digit)
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 255, message = "Password must be between 8 and 255 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[\\S]+$",
            message = "Password must contain at least one lowercase letter, one uppercase letter, one digit, and no whitespace")
    private String password;

    /**
     * Email address (optional)
     */
    @Email(message = "Invalid email format")
    private String email;
}
