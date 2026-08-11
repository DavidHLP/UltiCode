package com.ulticode.app.api.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * P3-OWNER-001-E / ADMIN-006: owner-only admin read surface for the
 * {@code solutions} table in the solution module.
 *
 * <p>Consumed by the Admin service's {@code AdminSolutionProjection}
 * (paginated list, flagged-list derivation and single-detail read). Returns
 * flat typed rows — never the internal {@code Solution} entity or mapper.
 * The provider owns both query branches (active rows via MyBatis-Plus
 * logical-delete filtering, soft-deleted rows via the raw-SQL pair) so the
 * consumer stays entity-free.
 *
 * @author ulticode
 */
public interface SolutionAdminReadPort {

    /**
     * Flat, entity-free projection of a {@code solutions} row.
     */
    record SolutionAdminRow(
            String id,
            Long problemId,
            String userId,
            String title,
            String content,
            String summary,
            String language,
            String tags,
            Integer views,
            Boolean isPublished,
            LocalDateTime publishedAt,
            String publishedBy,
            Boolean isFlagged,
            String flaggedReason,
            LocalDateTime flaggedAt,
            Boolean isDeleted,
            LocalDateTime deletedAt,
            String deletedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    /**
     * Paginated page of {@link SolutionAdminRow}.
     */
    record SolutionAdminPage(List<SolutionAdminRow> rows, long total) {}

    /**
     * Filter/sort/pagination parameters for the admin solution list read.
     *
     * @param search         matches {@code title} OR {@code content} LIKE
     * @param problemId      optional problem filter
     * @param userId         optional author filter
     * @param isFlagged      optional flagged-state filter
     * @param isPublished    optional published-state filter
     * @param includeDeleted when {@code true} the soft-deleted raw-SQL branch
     *                       bypasses MyBatis-Plus logical-delete filtering
     * @param sortBy         {@code title}, {@code views}, {@code createdAt} or
     *                       {@code updatedAt} (default {@code createdAt})
     * @param sortOrder      {@code asc} or {@code desc} (default {@code desc})
     * @param page           1-based page number
     * @param limit          page size
     */
    record SolutionAdminQuery(
            String search,
            Long problemId,
            String userId,
            Boolean isFlagged,
            Boolean isPublished,
            boolean includeDeleted,
            String sortBy,
            String sortOrder,
            int page,
            int limit) {}

    /**
     * Paginated admin query over solutions.
     *
     * @param query filters, sort and pagination
     * @return matching rows plus the total count
     */
    SolutionAdminPage page(SolutionAdminQuery query);

    /**
     * Single solution row by id (logical-delete aware).
     *
     * @param id solution ID
     * @return the row, or {@code null} when the solution does not exist
     */
    SolutionAdminRow getById(String id);
}
