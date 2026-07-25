package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.BulkActionResult;
import com.ulticode.modules.admin.dto.AuditLogVO;

import java.util.List;

/**
 * Write-side service interface for admin forum post management after the
 * ADR-0011 Stage 2 extraction.
 *
 * <p>Read paths (paginated post list, single-detail post, community list)
 * moved to
 * {@link com.ulticode.modules.admin.projection.AdminForumProjection}. This
 * interface keeps the write state machine (pin / unpin / lock / unlock /
 * soft-delete / flag / unflag / bulk action) plus the audit-history
 * delegation.
 *
 * @author ulticode
 */
public interface AdminForumService {

    /**
     * Pin a post.
     *
     * @param id post ID
     */
    void pinPost(String id);

    /**
     * Unpin a post.
     *
     * @param id post ID
     */
    void unpinPost(String id);

    /**
     * Lock a post (no comments allowed).
     *
     * @param id post ID
     */
    void lockPost(String id);

    /**
     * Unlock a post.
     *
     * @param id post ID
     */
    void unlockPost(String id);

    /**
     * Delete a post (soft delete).
     *
     * @param id post ID
     */
    void deletePost(String id);

    /**
     * Perform bulk action on multiple posts.
     *
     * @param ids    list of post IDs
     * @param action action to perform (delete, pin, unpin, lock, unlock, unflag)
     * @return bulk action result
     */
    BulkActionResult bulkAction(List<String> ids, String action);

    /**
     * Get audit history for a forum post.
     *
     * @param id post ID
     * @return list of audit log VOs
     */
    List<AuditLogVO> getPostAuditHistory(String id);

    /**
     * Flag a post for review.
     *
     * @param id     post ID
     * @param reason reason for flagging
     */
    void flagPost(String id, String reason);

    /**
     * Unflag a post.
     *
     * @param id post ID
     */
    void unflagPost(String id);
}
