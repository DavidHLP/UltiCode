package com.ulticode.auth.security.jwt;

import com.ulticode.websecurity.jwt.AccessTokenClaims;
import com.ulticode.websecurity.jwt.AccessTokenVerifier;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Adapts Auth's local signing keys to the shared access-token filter contract. */
@Component
@RequiredArgsConstructor
public class AuthAccessTokenVerifier implements AccessTokenVerifier {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public AccessTokenClaims verify(String token) {
        Claims claims = jwtTokenProvider.parseToken(token);
        if (claims == null || "refresh".equalsIgnoreCase(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Token is not a valid access token");
        }
        return new AccessTokenClaims(
                claims.getSubject(),
                claims.get("username", String.class),
                claims.get("role", String.class));
    }
}
