package com.ulticode.modules.auth.service.oauth;

/**
 * User-info normalized across providers.
 *
 * <p>Field mapping is provider-specific (GitHub exposes {@code id/name/
 * email/avatar_url}, Google exposes {@code id/email/name/picture} +
 * {@code email_verified}). The adapter does the field-by-field extraction
 * so the coordinator only sees this uniform shape and runs the DB
 * upsert tail.
 *
 * <p>Phase 0 / MICROSERVICE_MIGRATION_GUIDE.md §7.1: providers must
 * prove the email is verified before we auto-link an OAuth identity to
 * an existing account by email. {@code emailVerified} captures that
 * signal. The default is {@code false} — callers that don't yet
 * populate it (existing test fixtures) must explicitly opt in by
 * constructing a record with {@code true} once the adapter verifies.
 *
 * @param providerId     the provider's stable user id (string form)
 * @param name           the display name, falling back to a sensible default
 * @param email          the verified email, or {@code null} if the provider
 *                       did not expose one (no verification possible)
 * @param avatar         the avatar URL, or {@code null} if none
 * @param emailVerified  {@code true} iff the provider confirmed the email
 *                       is owned by the user (Google {@code email_verified};
 *                       GitHub email field present on /user implies public
 *                       + verified). Default {@code false} so a missing
 *                       flag downgrades to "unverified" rather than
 *                       silently admitting unverified auto-link.
 */
public record OAuthUserInfo(String providerId, String name, String email,
                            String avatar, boolean emailVerified) {

    /**
     * Convenience constructor for tests that haven't migrated to the
     * verified-aware signature. Defaults {@code emailVerified=false}.
     */
    public OAuthUserInfo(String providerId, String name, String email, String avatar) {
        this(providerId, name, email, avatar, false);
    }
}