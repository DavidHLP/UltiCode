package com.ulticode.auth.dto;

import com.ulticode.auth.api.dto.UserIdentityDTO;
import lombok.Builder;
import lombok.Data;

/**
 * Login response DTO containing CSRF token and user identity.
 */
@Data
@Builder
public class LoginResponse {

    private String csrfToken;
    private UserIdentityDTO user;
}
