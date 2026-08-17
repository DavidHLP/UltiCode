package com.ulticode.common.auth;

import java.io.Serializable;

/**
 * Credential-free JWT claim projection returned after token validation.
 */
public record JwtPayload(String userId, String username, String role) implements Serializable {
    private static final long serialVersionUID = 1L;
}
