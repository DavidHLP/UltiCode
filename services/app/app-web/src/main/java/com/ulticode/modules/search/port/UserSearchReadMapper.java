package com.ulticode.modules.search.port;

import java.util.List;

/**
 * Consumer port for the user search read model.
 *
 * <p>Account fields are resolved through the Auth owner and profile fields
 * through the App-owned profile read seam by the Spring adapter. This
 * interface deliberately contains no datasource annotations so App cannot
 * accidentally read Auth-owned {@code users} through its datasource.
 */
public interface UserSearchReadMapper {

    /**
     * Find non-deleted accounts whose username or App-owned profile name
     * contains the query, ordered deterministically by username.
     */
    List<UserSearchRow> searchIndex(String query, int limit);

    /**
     * Read one complete, index-safe row for a non-deleted account.
     */
    UserSearchRow findIndexRowById(String id);

    /**
     * Enumerate non-deleted accounts in stable id order for search backfill.
     */
    List<UserSearchRow> enumerateIndex(int offset, int limit);
}
