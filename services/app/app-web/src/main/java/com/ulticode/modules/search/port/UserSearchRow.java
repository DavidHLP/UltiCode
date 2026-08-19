package com.ulticode.modules.search.port;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Lightweight owner-composed row for the user search index.
 *
 * <p>Account fields are supplied by Auth and profile fields by App. The row
 * contains only index-safe display data and backfill timestamps.
 */
@Getter
@Setter
public class UserSearchRow {

    private String id;
    private String username;
    private String name;
    private String avatar;

    /**
     * Auth-owned lifecycle timestamp exposed by the account query projection.
     * It is used when available as the account-side backfill watermark.
     */
    private LocalDateTime updatedAt;

    /** user_profiles.updated_at (App-owned profile writes). */
    private LocalDateTime profileUpdatedAt;

    /** Retained for the legacy row contract; non-deleted owner reads leave it null. */
    private LocalDateTime deletedAt;

    private LocalDateTime joinedAt;
}
