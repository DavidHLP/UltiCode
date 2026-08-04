package com.ulticode.app.security.jwt;

import com.ulticode.app.api.dto.JwtPayload;
import com.ulticode.app.api.service.JwtValidationPort;
import io.jsonwebtoken.Claims;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * App-local adapter implementing {@link JwtValidationPort} by delegating to
 * the existing {@link ResourceServerJwtVerifier} offline verifier.
 *
 * <p>This closes the bean gap created by P7-RELOCATE-WEBSOCKET-001: the
 * websocket module needs JWT validation without a compile dependency on
 * backend-auth, and this adapter provides it using the same shared secret.
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
