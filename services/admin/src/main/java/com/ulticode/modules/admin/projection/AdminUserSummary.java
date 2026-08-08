package com.ulticode.modules.admin.projection;

/**
 * Merged user display data from Auth identity and App profile RPC sources.
 *
 * <p>Used by admin projections and adapters to enrich VOs with username,
 * role, display name, avatar, and email without importing Legacy
 * {@code User} or {@code UserMapper}.
 *
 * <p>Email is only populated by single-fetch ({@link AdminUserEnricher#enrichOne});
 * batch enrichment leaves it null since batch list views do not display email.
 */
public record AdminUserSummary(
        String accountId,
        String username,
        String role,
        String name,
        String avatar,
        String email) {
}
