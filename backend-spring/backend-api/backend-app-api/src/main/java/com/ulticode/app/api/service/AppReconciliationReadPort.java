package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ReconciliationOrphanCounts;

/**
 * App-owned reconciliation read port.
 *
 * <p>Provides the App-side facts the nightly reconciliation aggregator
 * needs (ADR-P7-OWNER-BOUNDARY-RECONCILIATION-20260802 Decision 4):
 * the user_profiles row count for the users → user_profiles dual-write
 * pair, and orphan counts for the nine App child references pointing
 * at the Auth-owned users table. The provider implements the orphan
 * predicate as App-local SQL against App-owned tables plus a Q-read of
 * the shared users table (allowed by ADR-P7-APP-DECOMPOSITION rule 3).
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
