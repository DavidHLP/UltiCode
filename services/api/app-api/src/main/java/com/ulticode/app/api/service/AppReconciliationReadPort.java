package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ReconciliationOrphanCounts;

/**
 * App-owned reconciliation read port.
 *
 * <p>Provides App-side facts for the nightly reconciliation aggregator:
 * the `user_profiles` row count and orphan counts for current App-owned child
 * references to Auth-owned accounts. Submission and Notification orphan facts
 * are no longer read from App; they come from their owner contracts. The
 * provider reads child ids only from App-owned tables and resolves physical
 * account existence through the Auth reconciliation contract; no shared-table
 * Q-read is permitted.
 */
public interface AppReconciliationReadPort {

    /**
     * Row count of the App-owned {@code user_profiles} table (the App
     * half of the users → user_profiles pair).
     */
    long countUserProfiles();

    /**
     * Orphan counts for App-owned child references to users. The Submission
     * and Notification components in the DTO are deprecated zero placeholders;
     * their owner facts are read through their own contracts.
     * A child row is an orphan only when the parent id does not exist
     * at all (soft-deleted parents are NOT orphans).
     */
    ReconciliationOrphanCounts countOrphans();
}
