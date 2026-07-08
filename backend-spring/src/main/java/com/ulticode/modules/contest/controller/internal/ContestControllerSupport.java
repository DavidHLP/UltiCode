package com.ulticode.modules.contest.controller.internal;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.projection.ContestProjection;

/**
 * Shared package-private helpers for the contest controller family.
 *
 * <p>Kept under {@code .internal} so callers know these are not part of the
 * public controller API. The previous god class mixed these into a 710-LoC file;
 * the split controllers now reach for the same two helpers (id resolution +
 * current-user-id) without sharing state.
 */
public final class ContestControllerSupport {

    private ContestControllerSupport() {
        // utility holder
    }

    /**
     * Resolve a contest ID or slug to the actual database contest ID.
     * Throws 404 when neither id nor slug matches (no silent fallback).
     *
     * @param contestProjection the contest read-side projection (owns entity accessors)
     * @param idOrSlug          the raw path variable (contest id or slug)
     * @return the resolved database contest id
     */
    public static String resolveContestId(ContestProjection contestProjection, String idOrSlug) {
        if (idOrSlug == null) {
            return null;
        }
        return contestProjection.findById(idOrSlug)
                .map(Contest::getId)
                .or(() -> contestProjection.findBySlug(idOrSlug).map(Contest::getId))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CONTEST_NOT_FOUND,
                        "Contest not found by id or slug: " + idOrSlug));
    }

    /**
     * Get the current authenticated user's ID or throw 401.
     */
    public static String getCurrentUserIdOrThrow(CurrentUserProvider currentUserProvider) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
