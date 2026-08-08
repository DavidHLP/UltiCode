package com.ulticode.auth.service;

import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Auth-owned authorization snapshot query seam.
 *
 * <p>The query owns account, role-template, and direct-permission reads and
 * returns transport-safe DTOs. Callers do not receive persistence entities or
 * mapper types.</p>
 */
public interface AuthorizationSnapshotQuery {

    /**
     * Loads one account's effective authorization snapshot.
     *
     * @param accountId account identifier
     * @return the snapshot when the account exists, otherwise empty
     */
    Optional<AuthorizationSnapshotDTO> getSnapshot(String accountId);

    /**
     * Loads snapshots for known account identifiers. Unknown identifiers are
     * omitted and null/empty input returns an empty list.
     *
     * @param accountIds account identifiers
     * @return one snapshot per known account
     */
    List<AuthorizationSnapshotDTO> batchGetSnapshot(Set<String> accountIds);
}
