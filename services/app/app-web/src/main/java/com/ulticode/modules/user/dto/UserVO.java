package com.ulticode.modules.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User View Object for API responses.
 * Excludes sensitive data like password.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserVO {

    /**
     * User unique identifier
     */
    private String id;

    /**
     * Unique username
     */
    private String username;

    /**
     * Display name
     */
    private String name;

    /**
     * Email address (only shown to self or admins)
     */
    private String email;

    /**
     * Avatar URL
     */
    private String avatar;

    /**
     * User biography
     */
    private String bio;

    /**
     * Company name
     */
    private String company;

    /**
     * GitHub profile URL
     */
    private String github;

    /**
     * User registration timestamp
     */
    private LocalDateTime joinedAt;

    /**
     * User location
     */
    private String location;

    /**
     * Twitter profile URL
     */
    private String twitter;

    /**
     * Personal website URL
     */
    private String website;

    /**
     * Preferred programming language
     */
    private String preferredLanguage;

    /**
     * User role
     */
    private String role;

    /**
     * Whether the user account is active
     */
    private Boolean isActive;

    /**
     * Last login timestamp
     */
    private LocalDateTime lastLoginAt;
}
