package com.ulticode.app.api.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ADMIN-007: owner-only admin read surface for {@code forum_tags}.
 *
 * <p>Consumed by the Admin service's {@code ForumTagHandler} (list and
 * single-detail reads). Returns flat typed rows — never the internal
 * {@code ForumTag} entity or mapper. Name / slug conflict detection is
 * owned by {@link ForumTagAdministrationService} (race-free, provider
 * side); this port stays read-only.
 *
 * @author ulticode
 */
public interface ForumTagReadPort {

    /**
     * Flat, entity-free projection of a {@code forum_tags} row.
     */
    record ForumTagRow(
            String id,
            String name,
            String slug,
            String description,
            String color,
            Integer usageCount,
            LocalDateTime createdAt) {}

    /**
     * Paginated page of {@link ForumTagRow}.
     */
    record ForumTagPage(List<ForumTagRow> rows, long total) {}

    /**
     * Paginated query over forum tags.
     *
     * @param search    optional match across {@code name} OR {@code slug}
     *                  LIKE
     * @param pageNum   1-based page number
     * @param pageSize  page size
     * @param sortBy    {@code usageCount} (or {@code usage_count}) sorts by
     *                  usage count, any other value sorts by name
     * @param sortOrder {@code asc} or any other value for {@code desc}
     * @return matching rows plus the total count
     */
    ForumTagPage page(String search, int pageNum, int pageSize, String sortBy, String sortOrder);

    /**
     * Single forum tag row by id.
     *
     * @param id tag ID
     * @return the row, or {@code null} when the tag does not exist
     */
    ForumTagRow getById(String id);
}
