package com.ulticode.app.api.dto;

import java.util.Optional;

/**
 * JWT payload extracted from a validated token.
 * Extracted from auth.util.JwtUtils for P7-RELOCATE-WEBSOCKET-001.
 */
public record JwtPayload(String userId, String username, String role) {
}
