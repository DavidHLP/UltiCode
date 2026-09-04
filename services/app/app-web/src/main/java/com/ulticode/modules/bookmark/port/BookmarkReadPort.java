package com.ulticode.modules.bookmark.port;

/**
 * Read-side port for bookmark favorites count queries owned by the App service.
 *
 * <p>Consumed by the legacy edge-operations module's
 * {@code DefaultEdgeOperationInspector} to read bookmark favorites counts
 * without importing the App module's internal mapper or entity. This is the
 * provider-owned contract pattern: the interface lives in
 * {@code backend-app-api}, the implementation in {@code backend-app}, and
 * the consumer depends only on the contract module.
 *
 * <p>P7-APP-BOOKMARK-001: extracted when the bookmark family relocated
 * from backend-legacy to backend-app. The edge-operations inspector
 * previously injected {@code BookmarkMapper} directly; it now injects
 * this port.
 */
public interface BookmarkReadPort {

    /**
     * Count how many users favorited/bookmarked a given target.
     *
     * <p>Non-leaf target types (those not in the bookmark module's
     * leaf-type set) short-circuit to {@code 0} without a database
     * round-trip. This preserves the optimisation the caller previously
     * inlined via {@code BookmarkType.leafTypeNames()}.
     *
     * @param targetType the target type string (e.g. {@code "PROBLEM"},
     *                   {@code "SOLUTION"}, {@code "FORUM_POST"})
     * @param targetId   the target entity ID
     * @return favorites count, or {@code 0} for non-leaf target types
     */
    long countFavoritesByTarget(String targetType, String targetId);
}
