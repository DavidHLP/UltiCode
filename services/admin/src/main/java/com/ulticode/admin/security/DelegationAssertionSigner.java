package com.ulticode.admin.security;

import com.ulticode.common.security.DelegationAssertionContract;
import com.ulticode.websecurity.jwt.RsaKeyMaterial;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Issues short-lived RS256 identity assertions for Admin-to-owner writes. */
@Component
public class DelegationAssertionSigner {

    private static final Set<String> ALLOWED_TARGET_APPLICATIONS =
            Set.of("backend-app", "backend-auth", "backend-notification",
                    "backend-submission");

    @Value("${security.internal-delegation.private-key:}")
    private String privateKeyBase64;

    @Value("${security.internal-delegation.key-id:}")
    private String keyId;

    @Value("${security.internal-delegation.bootstrap-private-key:}")
    private String bootstrapPrivateKeyBase64;

    @Value("${security.internal-delegation.bootstrap-key-id:}")
    private String bootstrapKeyId;

    @Value("${app.bootstrap-admin.enabled:false}")
    private boolean productionBootstrapEnabled;

    @Value("${app.dev-users.enabled:false}")
    private boolean developmentBootstrapEnabled;

    @Value("${security.internal-delegation.issuer:" + DelegationAssertionContract.ISSUER + "}")
    private String issuer;

    @Value("${security.internal-delegation.ttl-seconds:30}")
    private long ttlSeconds;

    private volatile PrivateKey privateKey;
    private volatile PrivateKey bootstrapPrivateKey;

    @PostConstruct
    void validateConfiguration() {
        if (ttlSeconds < 1 || ttlSeconds > 60) {
            throw new IllegalStateException("Delegation assertion TTL must be between 1 and 60 seconds");
        }
        privateKey = loadConfiguredKey(privateKeyBase64, "delegation");
        bootstrapPrivateKey = loadConfiguredKey(bootstrapPrivateKeyBase64, "bootstrap delegation");
        if (privateKey != null && bootstrapPrivateKey != null
                && Arrays.equals(privateKey.getEncoded(), bootstrapPrivateKey.getEncoded())) {
            throw new IllegalStateException("Delegation and bootstrap signing keys must be different");
        }
    }

    /**
     * Issue an assertion for the authenticated Admin request principal and
     * bind it to the concrete Dubbo target application.
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
        PrivateKey signingKey = normalSigningKey();
        if (actorType == null || signingKey == null || keyId == null || keyId.isBlank()) {
            return null;
        }
        return issue(normalizedTarget, authentication.getName(), actorType, signingKey, keyId, false);
    }

    /**
     * Issue the narrowly scoped assertion used only by an explicit one-shot
     * bootstrap command. It never falls back to the normal delegation key.
     */
    public String issueForBootstrap(String targetApplication) {
        String normalizedTarget = targetApplication == null ? "" : targetApplication.trim();
        if (!"backend-auth".equals(normalizedTarget)
                || (!productionBootstrapEnabled && !developmentBootstrapEnabled)
                || bootstrapKeyId == null || bootstrapKeyId.isBlank()) {
            return null;
        }
        PrivateKey signingKey = bootstrapSigningKey();
        return signingKey == null
                ? null
                : issue(normalizedTarget, "bootstrap", "BOOTSTRAP", signingKey, bootstrapKeyId, true);
    }

    private String issue(
            String targetApplication, String subject, String actorType,
            PrivateKey signingKey, String signingKid, boolean bootstrap) {
        try {
            Instant issuedAt = Instant.now();
            Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);
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
            return builder.header().keyId(signingKid).and()
                    .signWith(signingKey, Jwts.SIG.RS256)
                    .compact();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private PrivateKey normalSigningKey() {
        PrivateKey key = privateKey;
        if (key == null) {
            key = loadConfiguredKey(privateKeyBase64, "delegation");
            privateKey = key;
        }
        return key;
    }

    private PrivateKey bootstrapSigningKey() {
        PrivateKey key = bootstrapPrivateKey;
        if (key == null) {
            key = loadConfiguredKey(bootstrapPrivateKeyBase64, "bootstrap delegation");
            bootstrapPrivateKey = key;
        }
        return key;
    }

    private static PrivateKey loadConfiguredKey(String encoded, String label) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return RsaKeyMaterial.loadPrivateKey(encoded);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid " + label + " signing key", exception);
        }
    }
}
