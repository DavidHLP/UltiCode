package com.ulticode.common.auth;

import java.io.Serializable;

/**
 * Credential-free account projection used by local authentication seams.
 * Auth remains authoritative for the underlying account facts.
 */
public record AccountInfo(
        String id,
        String username,
        String role,
        boolean isActive,
        boolean isBanned) implements Serializable {
    private static final long serialVersionUID = 1L;
}
