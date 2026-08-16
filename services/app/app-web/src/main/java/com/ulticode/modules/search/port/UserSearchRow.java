package com.ulticode.modules.search.port;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Lightweight read row for the user search index (Q-read of the
 * Auth-owned {@code users} table). Holds only display/identity
 * columns — never credentials.
 */
@Getter
@Setter
public class UserSearchRow {

    private String id;
    private String username;
    private String name;
    private String avatar;

    /** users.updated_at (identity/ban writes; V20260816220000). */
    private LocalDateTime updatedAt;

    /** user_profiles.updated_at (App-owned profile writes). */
    private LocalDateTime profileUpdatedAt;

    private LocalDateTime deletedAt;

    private LocalDateTime joinedAt;
}
