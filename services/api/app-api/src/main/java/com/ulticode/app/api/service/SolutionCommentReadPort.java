package com.ulticode.app.api.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ADMIN-006: owner-only admin read surface for {@code solution_comments}.
 *
 * <p>Consumed by the Admin service's {@code SolutionCommentModerator} (list
 * and single-detail reads). Returns flat typed rows — never the internal
 * {@code SolutionComment} entity or mapper. All reads ignore logical delete
 * so admins can audit removed rows, matching the former
 * {@code selectPageIgnoreDeleted}/{@code selectByIdIgnoreDeleted} contract.
 *
 * @author ulticode
 */
public interface SolutionCommentReadPort {

    /**
     * Flat, entity-free projection of a {@code solution_comments} row.
     */
    record SolutionCommentRow(
            String id,
            String content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String userId,
            String parentId,
            String solutionId,
            Boolean isFlagged,
            String flaggedReason,
            LocalDateTime flaggedAt,
            Boolean isDeleted,
            LocalDateTime deletedAt,
            String deletedBy) {}

    /**
     * Paginated page of {@link SolutionCommentRow}.
     */
    record SolutionCommentPage(List<SolutionCommentRow> rows, long total) {}

    /**
     * Paginated query over solution comments ignoring logical delete.
     *
     * @param isFlagged   optional flagged-state filter
     * @param isDeleted   optional deleted-state filter
     * @param search      matches {@code content} LIKE
     * @param solutionId  optional parent-solution filter (admin passes the
     *                    comment-query {@code parentEntityId})
     * @param sortBy      {@code updatedAt} maps to {@code updated_at}, any
     *                    other value sorts by {@code created_at}
     * @param sortOrder   {@code asc} or any other value for {@code desc}
     * @param page        1-based page number
     * @param limit       page size
     * @return matching rows plus the total count
     */
    SolutionCommentPage page(Boolean isFlagged, Boolean isDeleted, String search, String solutionId,
                             String sortBy, String sortOrder, int page, int limit);

    /**
     * Single comment row by id ignoring logical delete.
     *
     * @param commentId comment ID
     * @return the row, or {@code null} when the comment does not exist
     */
    SolutionCommentRow getById(String commentId);
}
