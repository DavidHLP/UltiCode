package com.ulticode.modules.contest.controller.internal;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.service.ContestService;

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
     */
    public static String resolveContestId(ContestService contestService, String idOrSlug) {
        if (idOrSlug == null) {
            return null;
        }
        return contestService.findById(idOrSlug)
                .map(Contest::getId)
                .or(() -> contestService.findBySlug(idOrSlug).map(Contest::getId))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CONTEST_NOT_FOUND,
                        "Contest not found by id or slug: " + idOrSlug));
    }

    /**
     * Get the current authenticated user's ID or throw 401.
     */
    public static String getCurrentUserIdOrThrow() {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
