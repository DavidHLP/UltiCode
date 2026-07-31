package com.ulticode.app.api.dto;

/**
 * Result of a profile write operation, returned by
 * {@code ProfileWriteService.updateProfile}.
 *
 * <p>Carries only App-owned profile fields — the post-update state of
 * the {@code user_profiles} row. No account fields (email, password,
 * role, etc.) are exposed; those remain Auth-owned.
 */
public record ProfileWriteResult(
        String accountId,
        String name,
        String avatar,
        String bio,
        String company,
        String github,
        String location,
        String twitter,
        String website,
        String preferredLanguage) {
}
