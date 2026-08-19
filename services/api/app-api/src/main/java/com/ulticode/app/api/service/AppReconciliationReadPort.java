package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ReconciliationOrphanCounts;

/**
 * App-owned reconciliation read port.
 *
 * <p>Provides App-side facts for the nightly reconciliation aggregator:
 * the {@code user_profiles} row count and orphan counts for App child
 * references to Auth-owned accounts. The provider reads child ids only from
 * App-owned tables and resolves physical account existence through the Auth
 * reconciliation contract; no shared-table Q-read is permitted.
 */
public interface AppReconciliationReadPort {

    /**
     * Row count of the App-owned {@code user_profiles} table (the App
     * half of the users → user_profiles pair).
     */
    long countUserProfiles();

    /**
     * Orphan counts for all nine App child references to users.
     * A child row is an orphan only when the parent id does not exist
     * at all (soft-deleted parents are NOT orphans).
     */
    ReconciliationOrphanCounts countOrphans();
}
