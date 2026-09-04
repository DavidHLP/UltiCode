package com.ulticode.modules.search.port;

import com.ulticode.app.api.dto.UserIndexDTO;

import java.util.List;

/**
 * Read-side port for the user search source.
 *
 * <p>The provider owns the non-deleted predicate, username/name LIKE
 * matching and limit enforcement. A null/blank query or non-positive
 * limit yields an empty list; the result is never null.
 *
 * <p>Migration-state rule (ADR-P7-APP-DECOMPOSITION rule 3): the App
 * service may Q-read the Auth-owned {@code users} table for account
 * display fields; when the physical DB splits later, this adapter
 * switches to the Auth identity query seam.
 */
public interface UserSearchReadPort {

    /**
     * Search the user index by username or display name.
     *
     * @param query username/name search text
     * @param limit maximum number of rows
     * @return matching index projections, never null
     */
    List<UserIndexDTO> searchForIndex(String query, int offset, int limit);

    default List<UserIndexDTO> searchForIndex(String query, int limit) {
        return searchForIndex(query, 0, limit);
    }

    long countForIndex(String query);
}
