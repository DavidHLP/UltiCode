package com.ulticode.websecurity.jwt;

import com.ulticode.common.auth.JwtPayload;
import com.ulticode.common.security.JwtValidationPort;
import io.jsonwebtoken.JwtException;
import java.util.Optional;

/** Shared adapter for consumers of the HTTP-neutral JWT validation port. */
public final class JwtValidationAdapter implements JwtValidationPort {

    private final AccessTokenVerifier verifier;

    public JwtValidationAdapter(AccessTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public Optional<JwtPayload> validateToken(String token) {
        try {
            AccessTokenClaims claims = verifier.verify(token);
            return Optional.of(new JwtPayload(claims.userId(), claims.username(), claims.role()));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> extractUserId(String token) {
        return validateToken(token).map(JwtPayload::userId);
    }
}
