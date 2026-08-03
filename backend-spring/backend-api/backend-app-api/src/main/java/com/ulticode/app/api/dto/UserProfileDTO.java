package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * App-owned user profile data carrier.
 *
 * <p>Contains public user profile attributes owned by {@code backend-app} in the
 * {@code user_profiles} table. Does not contain account attributes (such as
 * {@code username}, {@code email}, {@code password}, {@code role}, {@code active},
 * {@code banned}) which are owned by {@code backend-auth}.
 */
public record UserProfileDTO(
        String accountId,
        String name,
        String avatar,
        String bio,
        String company,
        String github,
        String location,
        String twitter,
        String website,
        String preferredLanguage) implements Serializable {

    /**
     * Factory for an empty profile stub when an account exists but has no custom profile.
     */
    public static UserProfileDTO empty(String accountId) {
        return new UserProfileDTO(accountId, null, null, null, null, null, null, null, null, null);
    }
}
