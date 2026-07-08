package com.ulticode.modules.auth.service.oauth;

/**
 * User-info normalized across providers.
 *
 * <p>Field mapping is provider-specific (GitHub exposes {@code id/name/
 * email/avatar_url}, Google exposes {@code id/email/name/picture}). The
 * adapter does the field-by-field extraction so the coordinator only
 * sees this uniform shape and runs the DB upsert tail.
 *
 * @param providerId the provider's stable user id (string form)
 * @param name       the display name, falling back to a sensible default
 * @param email      the verified email, or {@code null} if the provider
 *                   did not expose one
 * @param avatar     the avatar URL, or {@code null} if none
 */
public record OAuthUserInfo(String providerId, String name, String email, String avatar) {
}
