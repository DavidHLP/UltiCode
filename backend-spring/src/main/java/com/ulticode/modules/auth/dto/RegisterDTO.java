package com.ulticode.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
     * Password (6-255 characters)
     */
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 255, message = "Password must be between 6 and 255 characters")
    private String password;

    /**
     * Email address (optional)
     */
    @Email(message = "Invalid email format")
    private String email;
}
