package com.ulticode.app.user.port;

import java.time.LocalDateTime;

/** The smallest owner-composed account/profile view needed by directory consumers. */
public record UserFactView(
        String id,
        String username,
        String name,
        String avatar,
        LocalDateTime joinedAt,
        LocalDateTime authUpdatedAt,
        LocalDateTime profileUpdatedAt,
        LocalDateTime deletedAt,
        Boolean active,
        Boolean banned) {
}
