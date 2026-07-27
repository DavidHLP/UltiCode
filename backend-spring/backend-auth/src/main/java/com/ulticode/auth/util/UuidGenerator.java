package com.ulticode.auth.util;

/**
 * Seam interface for UUID generation inside backend-auth.
 */
public interface UuidGenerator {

    /**
     * Generate a new unique identifier string.
     */
    String newId();
}
