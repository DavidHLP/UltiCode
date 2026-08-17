package com.ulticode.common.security;

import com.ulticode.common.auth.JwtPayload;

import java.util.Optional;

/**
 * Port for validating a token and extracting its credential-free claims.
 */
public interface JwtValidationPort {
    Optional<JwtPayload> validateToken(String token);

    Optional<String> extractUserId(String token);
}
