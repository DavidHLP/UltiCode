package com.ulticode.modules.search.port;

import java.util.List;
import java.util.Set;

/**
 * Bounded, versioned read seam for the Search user directory.
 *
 * <p>The adapter behind this port owns the Auth account/identity and App
 * profile composition. Callers do not perform cross-Owner enrichment.
 */
public interface UserDirectoryQueryPort {

    int CONTRACT_VERSION = 1;

    List<UserDirectoryRow> search(String query, int limit);

    UserDirectoryRow findById(String accountId);

    List<UserDirectoryRow> enumerate(int offset, int limit);

    List<UserDirectoryRow> findByIds(Set<String> accountIds);
}
