package com.ulticode.modules.search.port;

import java.time.LocalDateTime;

/** Versioned Search user-directory row with owner freshness metadata. */
public record UserDirectoryRow(
        int contractVersion,
        UserSearchRow row,
        LocalDateTime authUpdatedAt,
        LocalDateTime profileUpdatedAt,
        LocalDateTime freshAt
) {
    public static UserDirectoryRow from(UserSearchRow row) {
        if (row == null) {
            return null;
        }
        LocalDateTime authUpdatedAt = row.getUpdatedAt();
        LocalDateTime profileUpdatedAt = row.getProfileUpdatedAt();
        LocalDateTime freshAt = authUpdatedAt;
        if (profileUpdatedAt != null && (freshAt == null || profileUpdatedAt.isAfter(freshAt))) {
            freshAt = profileUpdatedAt;
        }
        return new UserDirectoryRow(
                UserDirectoryQueryPort.CONTRACT_VERSION,
                row,
                authUpdatedAt,
                profileUpdatedAt,
                freshAt);
    }

    public UserDirectoryRow {
        if (contractVersion <= 0 || row == null) {
            throw new IllegalArgumentException("Invalid user directory row");
        }
    }
}
