package com.ulticode.auth.api.dto;

/**
 * Orphan-count snapshot for the four Auth-internal child references
 * that point at the Auth-owned {@code users} table.
 *
 * <p>Orphan semantics match the legacy scanner: a child row is an
 * orphan only if the referenced parent id does not exist <em>at
 * all</em> (soft-deleted parents still physically exist and are NOT
 * orphans).
 *
 * <p>P7-RECON-AGGREGATOR-001: extends the reconciliation contract so
 * the admin aggregator can obtain Auth-internal orphan facts without
 * cross-owner SQL.
 *
 * @param refreshTokens        orphaned refresh_tokens.user_id
 * @param passwordResets       orphaned password_resets.user_id
 * @param oauthProviderIdentities orphaned oauth_provider_identities.user_id
 * @param userPermissions      orphaned user_permissions.user_id
 */
public record AuthReconciliationOrphanCounts(
        long refreshTokens,
        long passwordResets,
        long oauthProviderIdentities,
        long userPermissions) {

    public static final AuthReconciliationOrphanCounts ZERO =
            new AuthReconciliationOrphanCounts(0, 0, 0, 0);
}
