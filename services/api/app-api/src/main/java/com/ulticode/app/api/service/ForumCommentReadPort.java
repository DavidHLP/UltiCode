package com.ulticode.app.api.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ADMIN-007: owner-only admin read surface for {@code forum_comments}.
 *
 * <p>Consumed by the Admin service's {@code ForumCommentModerator} (list
 * and single-detail reads). Returns flat typed rows — never the internal
 * {@code ForumComment} entity or mapper. All reads ignore logical delete
 * so admins can audit removed rows, matching the former
 * {@code selectPageIgnoreDeleted} / {@code selectByIdIgnoreDeleted}
 * contract.
 *
 * @author ulticode
 */
public interface ForumCommentReadPort {

    /**
     * Flat, entity-free projection of a {@code forum_comments} row.
     */
    record ForumCommentRow(
            String id,
            String body,
            LocalDateTime createdAt,
            LocalDateTime editedAt,
            String authorId,
            String parentId,
            String postId,
            Boolean isFlagged,
            String flaggedReason,
            LocalDateTime flaggedAt,
            Boolean isDeleted,
            LocalDateTime deletedAt,
            String deletedBy) {}

    /**
     * Paginated page of {@link ForumCommentRow}.
     */
    record ForumCommentPage(List<ForumCommentRow> rows, long total) {}

    /**
     * Paginated query over forum comments ignoring logical delete.
     *
     * @param isFlagged optional flagged-state filter
     * @param isDeleted optional deleted-state filter
     * @param search    matches {@code body} LIKE
     * @param postId    optional parent-post filter (admin passes the
     *                  comment-query {@code parentEntityId})
     * @param sortBy    {@code updatedAt} maps to {@code edited_at}, any
     *                  other value sorts by {@code created_at}
     * @param sortOrder {@code asc} or any other value for {@code desc}
     * @param page      1-based page number
     * @param limit     page size
     * @return matching rows plus the total count
     */
    ForumCommentPage page(Boolean isFlagged, Boolean isDeleted, String search, String postId,
                          String sortBy, String sortOrder, int page, int limit);

    /**
     * Single comment row by id ignoring logical delete.
     *
     * @param commentId comment ID
     * @return the row, or {@code null} when the comment does not exist
     */
    ForumCommentRow getById(String commentId);
}
