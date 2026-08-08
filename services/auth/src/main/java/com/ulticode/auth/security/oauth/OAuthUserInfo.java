package com.ulticode.auth.security.oauth;

/**
 * Normalized user info record returned by an {@link OAuthClient}.
 */
public record OAuthUserInfo(
        String providerId,
        String username,
        String name,
        String email,
        boolean emailVerified,
        String avatar
) {}
