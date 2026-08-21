package com.ulticode.app.user.port;

import java.time.LocalDateTime;

/** Transport-neutral account facts supplied to the owner-composed module. */
public record UserAccountFact(
        String id,
        String username,
        LocalDateTime joinedAt,
        LocalDateTime authUpdatedAt,
        LocalDateTime deletedAt,
        Boolean active,
        Boolean banned) {
}
