package com.ulticode.admin.security;

import com.ulticode.common.security.DelegationAssertionContract;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
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

    private static final Set<String> ALLOWED_TARGET_APPLICATIONS =
            Set.of("backend-app", "backend-auth", "backend-notification");

    @Value("${security.internal-delegation.secret:${jwt.secret:}}")
    private String secret;

    @Value("${security.internal-delegation.bootstrap-secret:}")
    private String bootstrapSecret;

    @Value("${app.bootstrap-admin.enabled:false}")
    private boolean productionBootstrapEnabled;

    @Value("${app.dev-users.enabled:false}")
    private boolean developmentBootstrapEnabled;

    @Value("${security.internal-delegation.issuer:" + DelegationAssertionContract.ISSUER + "}")
    private String issuer;

    @Value("${security.internal-delegation.ttl-seconds:30}")
    private long ttlSeconds;

    /**
     * Issue an assertion for the authenticated Admin request principal and
     * bind it to the concrete Dubbo target application.
     * Returns {@code null} when no authenticated privileged principal exists
     * or the target application cannot be established.
     */
    public String issueForTarget(String targetApplication) {
        String normalizedTarget = targetApplication == null ? "" : targetApplication.trim();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getPrincipal())
                || normalizedTarget.isBlank()
                || !ALLOWED_TARGET_APPLICATIONS.contains(normalizedTarget)) {
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
        return issue(normalizedTarget, authentication.getName(), actorType, secret, false);
    }

    /**
     * Issue the narrowly scoped assertion used only by an explicit one-shot
     * bootstrap command. It never falls back to the normal delegation secret.
     */
    public String issueForBootstrap(String targetApplication) {
        String normalizedTarget = targetApplication == null ? "" : targetApplication.trim();
        if (!"backend-auth".equals(normalizedTarget)
                || (!productionBootstrapEnabled && !developmentBootstrapEnabled)
                || bootstrapSecret == null || bootstrapSecret.isBlank()) {
            return null;
        }
        return issue(normalizedTarget, "bootstrap", "BOOTSTRAP", bootstrapSecret, true);
    }

    private String issue(
            String targetApplication, String subject, String actorType,
            String signingSecret, boolean bootstrap) {
        try {
            Instant issuedAt = Instant.now();
            Instant expiresAt = issuedAt.plusSeconds(Math.max(1L, ttlSeconds));
            SecretKey key = Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8));
            var builder = Jwts.builder()
                    .id(UUID.randomUUID().toString())
                    .subject(subject)
                    .claim(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, "backend-admin")
                    .claim(DelegationAssertionContract.ACTOR_TYPE_CLAIM, actorType)
                    .issuer(issuer)
                    .audience().add(targetApplication).and()
                    .issuedAt(Date.from(issuedAt))
                    .expiration(Date.from(expiresAt));
            if (bootstrap) {
                builder.claim(DelegationAssertionContract.BOOTSTRAP_CLAIM, true);
            }
            return builder.signWith(key, Jwts.SIG.HS256).compact();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
