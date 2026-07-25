package com.ulticode.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Login request DTO.
 */
@Data
public class LoginDTO {

    /**
     * Username for login
     */
    @NotBlank(message = "Username is required")
    private String username;

    /**
     * Password for login
     */
    @NotBlank(message = "Password is required")
    private String password;
}
