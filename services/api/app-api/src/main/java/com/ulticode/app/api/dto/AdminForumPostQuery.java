package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * ADMIN-007: filter / sort / pagination parameters for the admin forum
 * post list read.
 *
 * <p>Wire-safe mirror of the Admin service's
 * {@code AdminForumPostQueryDTO}; the Admin-side Dubbo adapter maps its
 * HTTP query DTO onto this record so {@code backend-app}'s
 * {@code AdminForumReadPort} provider stays entity-free.
 *
 * @param search       matches {@code title} OR {@code excerpt} LIKE
 * @param communityId  optional community filter
 * @param authorId     optional author filter
 * @param isFlagged    optional flagged-state filter
 * @param isPinned     optional pinned-state filter
 * @param isLocked     optional locked-state filter
 * @param isDeleted    optional deleted-state filter
 * @param sortBy       {@code createdAt}, {@code viewCount} or
 *                     {@code commentCount} (default {@code createdAt});
 *                     {@code commentCount} is sorted by the App owner
 *                     before pagination, with created-at/id tie-breaks
 * @param sortOrder    {@code asc} or {@code desc} (default {@code desc})
 * @param page         1-based page number
 * @param limit        page size
 */
public record AdminForumPostQuery(
        String search,
        String communityId,
        String authorId,
        Boolean isFlagged,
        Boolean isPinned,
        Boolean isLocked,
        Boolean isDeleted,
        String sortBy,
        String sortOrder,
        int page,
        int limit) implements Serializable {
    private static final long serialVersionUID = 1L;

}
