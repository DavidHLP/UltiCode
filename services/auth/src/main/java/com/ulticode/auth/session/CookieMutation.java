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
        String path) {

    public CookieMutation {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(path, "path");
        if (maxAgeSeconds < 0) {
            throw new IllegalArgumentException("maxAgeSeconds must not be negative");
        }
    }

    public static CookieMutation set(String name, String value, int maxAgeSeconds, boolean httpOnly) {
        return new CookieMutation(name, value, maxAgeSeconds, httpOnly, false, "/");
    }

    public static CookieMutation clear(String name, boolean httpOnly) {
        return new CookieMutation(name, "", 0, httpOnly, false, "/");
    }
}
