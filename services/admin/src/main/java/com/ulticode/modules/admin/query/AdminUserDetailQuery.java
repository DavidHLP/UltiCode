package com.ulticode.modules.admin.query;

/**
 * Admin-internal seam for loading one user's complete detail read model.
 *
 * <p>The implementation owns provider ordering, cross-owner aggregation, and
 * fail-closed availability semantics. Callers do not need to know which RPC
 * supplies a section or how many provider calls are required.
 */
public interface AdminUserDetailQuery {

    /**
     * Load one user's account, profile, statistics, and authorization facts.
     *
     * <p>A returned result distinguishes an authoritative not-found from an
     * owner transport failure. A found user may still have unavailable
     * optional sections; those sections are never represented as successful
     * empty values.
     *
     * @param userId Auth account identifier
     * @return detail result with top-level and per-section availability
     */
    AdminUserDetailResult loadUserDetail(String userId);
}
