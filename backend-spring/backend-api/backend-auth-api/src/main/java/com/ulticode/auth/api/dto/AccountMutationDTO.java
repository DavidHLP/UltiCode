package com.ulticode.auth.api.dto;

/**
 * Minimal password-free projection returned after an auth-owned account
 * mutation. The deleted flag is set only for the soft-delete operation.
 */
public record AccountMutationDTO(
        String accountId,
        String username,
        String email,
        String role,
        boolean active,
        boolean banned,
        long authzVersion,
        boolean deleted) {
}
