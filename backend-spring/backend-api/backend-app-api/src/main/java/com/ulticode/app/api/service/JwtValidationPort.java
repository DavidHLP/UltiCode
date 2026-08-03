package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.JwtPayload;

import java.util.Optional;

/**
 * Port for JWT validation and claim extraction.
 * Allows websocket module to validate tokens without depending on auth module.
 */
public interface JwtValidationPort {
    Optional<JwtPayload> validateToken(String token);
    Optional<String> extractUserId(String token);
}
