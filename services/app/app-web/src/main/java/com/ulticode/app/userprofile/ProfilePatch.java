package com.ulticode.app.userprofile;

/**
 * App-owned profile fields for a local mutation. Transport metadata and
 * receipt concerns stay in the adapters that build this patch.
 */
public record ProfilePatch(
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
