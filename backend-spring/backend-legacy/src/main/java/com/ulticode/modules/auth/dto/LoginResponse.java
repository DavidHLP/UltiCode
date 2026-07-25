package com.ulticode.modules.auth.dto;

import com.ulticode.modules.user.dto.UserVO;
import lombok.Builder;
import lombok.Data;

/**
 * Login response DTO containing CSRF token and user info.
 */
@Data
@Builder
public class LoginResponse {

    /**
     * CSRF token for client-side usage
     */
    private String csrfToken;

    /**
     * User information
     */
    private UserVO user;
}
