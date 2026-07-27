package com.ulticode.auth.dto;

import com.ulticode.auth.api.dto.UserIdentityDTO;
import lombok.Data;

/**
 * User view object carrying identity and CSRF token.
 */
@Data
public class UserWithCsrfVO {

    private UserIdentityDTO user;
    private String csrfToken;
}
