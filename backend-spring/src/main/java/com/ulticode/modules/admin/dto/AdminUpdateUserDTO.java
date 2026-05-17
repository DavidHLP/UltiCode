package com.ulticode.modules.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for admin updating user information.
 * All fields are optional - only provided fields will be updated.
 */
@Data
public class AdminUpdateUserDTO {

    /**
     * Unique username (max 50 characters)
     */
    @Size(max = 50, message = "Username must not exceed 50 characters")
    private String username;

    /**
     * Display name (max 120 characters)
     */
    @Size(max = 120, message = "Name must not exceed 120 characters")
    private String name;

    /**
     * Email address
     */
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    /**
     * Avatar URL
     */
    @Size(max = 255, message = "Avatar URL must not exceed 255 characters")
    private String avatar;

    /**
     * User biography
     */
    @Size(max = 5000, message = "Bio must not exceed 5000 characters")
    private String bio;

    /**
     * Company name
     */
    @Size(max = 255, message = "Company must not exceed 255 characters")
    private String company;

    /**
     * GitHub profile URL
     */
    @Size(max = 255, message = "GitHub URL must not exceed 255 characters")
    private String github;

    /**
     * Personal website URL
     */
    @Size(max = 255, message = "Website URL must not exceed 255 characters")
    private String website;

    /**
     * User location
     */
    @Size(max = 255, message = "Location must not exceed 255 characters")
    private String location;

    /**
     * Twitter profile URL
     */
    @Size(max = 255, message = "Twitter URL must not exceed 255 characters")
    private String twitter;

    /**
     * Preferred programming language
     */
    @Size(max = 50, message = "Preferred language must not exceed 50 characters")
    private String preferredLanguage;

    /**
     * User role
     */
    @Pattern(regexp = "USER|ADMIN|SUPER_ADMIN", message = "Role must be USER, ADMIN, or SUPER_ADMIN")
    private String role;

    /**
     * Active status
     */
    private Boolean isActive;
}
