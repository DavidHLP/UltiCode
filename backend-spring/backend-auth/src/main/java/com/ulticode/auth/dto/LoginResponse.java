package com.ulticode.auth.dto;

import com.ulticode.auth.dto.AuthUserVO;
import lombok.Builder;
import lombok.Data;

/**
 * Login response DTO containing CSRF token and user identity.
 */
@Data
@Builder
public class LoginResponse {

    private String csrfToken;
    private AuthUserVO user;
}
