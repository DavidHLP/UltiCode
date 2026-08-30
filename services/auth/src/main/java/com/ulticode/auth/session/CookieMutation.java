package com.ulticode.auth.session;

import java.util.Objects;

/**
 * An HTTP cookie mutation requested by an authentication workflow.
 *
 * <p>The value object contains policy data, while the HTTP adapter is solely
 * responsible for applying it to a servlet response.</p>
 */
public record CookieMutation(
        String name,
        String value,
        int maxAgeSeconds,
        boolean httpOnly,
        boolean secure,
        String sameSite,
        String path,
        String domain) {

    public CookieMutation {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(sameSite, "sameSite");
        Objects.requireNonNull(path, "path");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (maxAgeSeconds < 0) {
            throw new IllegalArgumentException("maxAgeSeconds must not be negative");
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/'");
        }
        if ("strict".equalsIgnoreCase(sameSite)) {
            sameSite = "Strict";
        } else if ("lax".equalsIgnoreCase(sameSite)) {
            sameSite = "Lax";
        } else if ("none".equalsIgnoreCase(sameSite)) {
            sameSite = "None";
        } else {
            throw new IllegalArgumentException("sameSite must be Strict, Lax, or None");
        }
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalArgumentException("SameSite=None requires Secure");
        }
        domain = domain == null || domain.isBlank() ? null : domain;
    }
}
