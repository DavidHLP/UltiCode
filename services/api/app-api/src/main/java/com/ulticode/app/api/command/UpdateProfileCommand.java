package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

/**
 * Command to update a user's profile fields. Issued against
 * {@code backend-app} {@code ProfileWriteService.updateProfile}.
 *
 * <p>Carries only App-owned profile fields (name, avatar, bio, company,
 * github, location, twitter, website, preferredLanguage). Account fields
 * (email, password, role, ban state) are Auth-owned and deliberately
 * absent — the provider rejects any request implying an account mutation.
 *
 * <p>All profile fields are optional (null = no change); at least the
 * {@code accountId} must be present so the provider can locate the
 * {@code user_profiles} row.
 *
 * <p>Per migration guide §6.2 the command carries {@code commandId},
 * {@link IdMetadata}, {@link ActorDelegation} and {@link TraceMetadata}
 * via the {@link WriteCommand} base contract.
 */
public record UpdateProfileCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String accountId,
        String name,
        String avatar,
        String bio,
        String company,
        String github,
        String location,
        String twitter,
        String website,
        String preferredLanguage) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    public UpdateProfileCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
        }
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException(
                    "accountId is required and must be a UUID String");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException(
                    "idempotency is required (use IdMetadata.mint() when "
                            + "no client token is available)");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
        // Boundary validation: enforce DB column lengths to prevent
        // MySQL truncation errors surfacing as UNEXPECTED_APP_STATE.
        // Limits mirror legacy UpdateUserDTO @Size constraints.
        requireSize(name, 120, "name");
        requireSize(avatar, 255, "avatar");
        requireSize(bio, 5000, "bio");
        requireSize(company, 255, "company");
        requireSize(github, 255, "github");
        requireSize(location, 255, "location");
        requireSize(twitter, 255, "twitter");
        requireSize(website, 255, "website");
        requireSize(preferredLanguage, 50, "preferredLanguage");
    }

    private static void requireSize(String value, int max, String fieldName) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + max + " characters");
        }
    }
}
