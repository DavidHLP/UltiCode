package com.ulticode.modules.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for admin creating a new user.
 */
@Data
public class AdminCreateUserDTO {

    /**
     * Unique username
     */
    @NotBlank(message = "Username is required")
    @Size(max = 50, message = "Username must not exceed 50 characters")
    private String username;

    /**
     * Email address
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    /**
     * Display name
     */
    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name must not exceed 120 characters")
    private String name;

    /**
     * Password used to initialize the Auth-owned credential.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 255, message = "Password must be between 6 and 255 characters")
    private String password;

    /**
     * User role (USER, ADMIN, SUPER_ADMIN)
     */
    @Pattern(regexp = "USER|ADMIN|SUPER_ADMIN", message = "Role must be USER, ADMIN, or SUPER_ADMIN")
    private String role;

    /**
     * Whether the user account is active
     */
    private Boolean isActive;
}
