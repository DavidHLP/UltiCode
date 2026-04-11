package com.ulticode.common.config;

import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import jakarta.annotation.PostConstruct;

/**
 * Validates critical environment variables on application startup.
 *
 * <p>In production, missing or weak values cause the application to fail fast.
 * In development, a warning is logged but startup continues.</p>
 */
@Configuration
public class EnvValidationConfig {

    private static final Logger log = LoggerFactory.getLogger(EnvValidationConfig.class);

    private static final int JWT_SECRET_MIN_LENGTH = 32;

    private static final Set<String> JWT_SECRET_BLACKLIST = Set.of(
        "dev-secret-key-for-local-development-must-be-at-least-32-chars",
        "secret",
        "password",
        "ulticode-super-secret-jwt-key-min-32-chars"
    );

    private final Environment environment;

    public EnvValidationConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateCriticalEnvVars() {
        List<String> requiredVars = List.of(
            "JWT_SECRET",
            "SPRING_DATASOURCE_URL"
        );

        List<String> missing = requiredVars.stream()
            .filter(var -> {
                String value = System.getenv(var);
                return value == null || value.isBlank();
            })
            .toList();

        if (!missing.isEmpty()) {
            String msg = "Missing required environment variables: " + String.join(", ", missing);
            failOrWarn(msg);
        }

        validateJwtSecretStrength();
    }

    private void validateJwtSecretStrength() {
        String jwtSecret = System.getenv("JWT_SECRET");
        if (jwtSecret == null || jwtSecret.isBlank()) {
            return; // already reported as missing
        }

        if (jwtSecret.length() < JWT_SECRET_MIN_LENGTH) {
            failOrWarn("JWT_SECRET is too short (" + jwtSecret.length() + " chars), minimum is "
                + JWT_SECRET_MIN_LENGTH + " chars");
        }

        if (JWT_SECRET_BLACKLIST.contains(jwtSecret)) {
            failOrWarn("JWT_SECRET matches a known weak default — generate a unique secret for this environment");
        }
    }

    private void failOrWarn(String msg) {
        if (environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            log.warn("{} — proceeding because dev/test profile is active", msg);
        } else {
            log.error(msg);
            throw new IllegalStateException(msg);
        }
    }
}
