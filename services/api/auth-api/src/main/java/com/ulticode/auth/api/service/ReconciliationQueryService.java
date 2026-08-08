package com.ulticode.auth.api.service;

import com.ulticode.auth.api.dto.AuthReconciliationOrphanCounts;
import com.ulticode.common.rpc.RpcResult;

import java.util.Collection;
import java.util.Set;

/**
 * Auth-owned reconciliation query provider.
 *
 * <p>Exposes the two count/existence facts the nightly reconciliation
 * aggregator needs from the Auth owner. Physical-existence semantics
 * deliberately differ from the identity queries: {@link #existingUserIds}
 * must return ids of rows that exist <em>at all</em> — including
 * soft-deleted accounts — because the orphan rule is "orphan = parent
 * id does not exist at all" (a soft-deleted parent is still a valid
 * logical reference, so its children are NOT orphans).
 *
 * <p>Contract-only interface; the provider implementation lives in
 * {@code backend-auth} (Dubbo service). Listed by
 * ADR-P7-OWNER-BOUNDARY-RECONCILIATION-20260802 (Decision 4):
 * reconciliation replaces cross-owner JdbcTemplate SQL with owner
 * RPC/read ports; no cross-owner DB grants are introduced.
 */
public interface ReconciliationQueryService {

    /**
     * Count non-deleted user accounts (the Auth half of the
     * users → user_profiles dual-write pair).
     *
     * @return success with the non-deleted row count
     */
    RpcResult<Long> countActiveUsers();

    /**
     * Return the subset of {@code ids} whose rows physically exist in
     * the users table. Soft-deleted rows ARE included (orphan
     * semantics: physical existence only).
     *
     * @param ids candidate account ids; {@code null} / empty returns
     *            an empty success set rather than an error
     * @return success with the existing ids (never null; unknown ids
     *         are omitted)
     */
    RpcResult<Set<String>> existingUserIds(Collection<String> ids);

    /**
     * Orphan counts for the four Auth-internal child references to the
     * users table (refresh_tokens / password_resets /
     * oauth_provider_identities / user_permissions). Physical-existence
     * semantics: a child is an orphan only when the parent id does not
     * exist at all.
     *
     * @return success with the four orphan counts
     */
    RpcResult<AuthReconciliationOrphanCounts> countAuthOrphans();
}
