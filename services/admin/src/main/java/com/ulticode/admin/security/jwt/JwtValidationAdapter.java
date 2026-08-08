package com.ulticode.admin.security.jwt;

import com.ulticode.app.api.dto.JwtPayload;
import com.ulticode.app.api.service.JwtValidationPort;
import io.jsonwebtoken.Claims;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Admin-local adapter implementing {@link JwtValidationPort} by delegating
 * to the admin shell's own {@link ResourceServerJwtVerifier} offline
 * verifier (P7-RELOCATE).
 *
 * <p>Mirrors the App-side {@code com.ulticode.app.security.jwt.JwtValidationAdapter}:
 * the websocket module (scanned into this context from backend-app) needs
 * JWT validation, while the App-owned security package is excluded from
 * the admin shell. Both sides validate with the same shared secret.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtValidationAdapter implements JwtValidationPort {

    private final ResourceServerJwtVerifier verifier;

    @Override
    public Optional<JwtPayload> validateToken(String token) {
        try {
            Claims claims = verifier.verifyAndParse(token);
            return Optional.of(new JwtPayload(
                    verifier.getUserId(claims),
                    verifier.getUsername(claims),
                    verifier.getRole(claims)));
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> extractUserId(String token) {
        try {
            Claims claims = verifier.verifyAndParse(token);
            return Optional.ofNullable(verifier.getUserId(claims));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
