package com.ulticode.admin.security;

import com.ulticode.app.api.security.DelegationAssertionContract;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Issues short-lived, signed identity assertions for Admin-to-App writes. */
@Component
public class DelegationAssertionSigner {

    @Value("${security.internal-delegation.secret:${jwt.secret:}}")
    private String secret;

    @Value("${security.internal-delegation.issuer:" + DelegationAssertionContract.ISSUER + "}")
    private String issuer;

    @Value("${security.internal-delegation.audience:" + DelegationAssertionContract.AUDIENCE + "}")
    private String audience;

    @Value("${security.internal-delegation.ttl-seconds:30}")
    private long ttlSeconds;

    /**
     * Issue an assertion for the authenticated Admin request principal.
     * Returns {@code null} when no authenticated privileged principal exists.
     */
    public String issueForCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        String actorType = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> "ROLE_ADMIN".equals(authority)
                        || "ROLE_SUPER_ADMIN".equals(authority))
                .map(authority -> "ROLE_SUPER_ADMIN".equals(authority) ? "SUPER_ADMIN" : "ADMIN")
                .findFirst()
                .orElse(null);
        if (actorType == null || secret == null || secret.isBlank()) {
            return null;
        }

        try {
            Instant issuedAt = Instant.now();
            Instant expiresAt = issuedAt.plusSeconds(Math.max(1L, ttlSeconds));
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            return Jwts.builder()
                    .id(UUID.randomUUID().toString())
                    .subject(authentication.getName())
                    .claim(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, "backend-admin")
                    .claim(DelegationAssertionContract.ACTOR_TYPE_CLAIM, actorType)
                    .issuer(issuer)
                    .audience().add(audience).and()
                    .issuedAt(Date.from(issuedAt))
                    .expiration(Date.from(expiresAt))
                    .signWith(key, Jwts.SIG.HS256)
                    .compact();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
