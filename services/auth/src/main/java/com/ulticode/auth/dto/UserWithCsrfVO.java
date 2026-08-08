package com.ulticode.auth.dto;

import lombok.Data;

/**
 * User view object carrying identity and CSRF token.
 * Returned by {@code /auth/me}.
 */
@Data
public class UserWithCsrfVO {

    private AuthUserVO user;
    private String csrfToken;
}
