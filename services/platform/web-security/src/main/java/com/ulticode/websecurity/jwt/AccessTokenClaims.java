package com.ulticode.websecurity.jwt;

import java.util.Objects;
import java.util.regex.Pattern;

/** Verified identity and authority data from an Auth-issued access token. */
public record AccessTokenClaims(String userId, String username, String role) {

    private static final Pattern ROLE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public AccessTokenClaims {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(role, "role");
        if (userId.isBlank() || username.isBlank() || role.isBlank()) {
            throw new IllegalArgumentException("Access-token identity claims must not be blank");
        }
        if (!ROLE_PATTERN.matcher(role).matches()) {
            throw new IllegalArgumentException("Access-token role is invalid");
        }
    }
}
