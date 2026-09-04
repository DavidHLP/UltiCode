package com.ulticode.auth.api.service;

import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.common.rpc.RpcResult;

import java.util.List;
import java.util.Set;

/**
 * Auth-owned query provider exposing the post-write authorization
 * snapshot for read-only consumers (Admin BFF detail pages, App
 * enrichments, scheduled jobs).
 *
 * <p>Listed in {@code docs/architecture/modules.md} as one of
 * {@code backend-auth}'s three Dubbo providers.
 * It mirrors {@link IdentityQueryService}'s shape (single + batch), so the two
 * query providers are symmetric and the call sites can share a
 * fan-out helper. Reads only; no write methods live here &mdash;
 * authorization mutations are owned by {@link AuthorizationMutationService}
 * and role changes by {@link RoleMutationService}.
 * <p>This interface is contract-only; no ServiceImpl lives in this
 * module. The provider implementation belongs to {@code backend-auth}.
 */
public interface AuthorizationSnapshotService {

    /**
     * Look up a single authorization snapshot by account id.
     *
     * @param accountId UUID String; must be non-blank
     * @return success with payload, or failure carrying
     *         {@code ACCOUNT_NOT_FOUND} when the id is unknown
     */
    RpcResult<AuthorizationSnapshotDTO> getSnapshot(String accountId);

    /**
     * Batch lookup. Provider must dedupe and return one
     * {@link AuthorizationSnapshotDTO} per known id; unknown ids are
     * simply omitted from the result list (callers use
     * {@code response.data().size() < ids.size()} as a presence map).
     *
     * <p>Maximum batch size is the provider's choice; the contract
     * does not pin it to avoid coupling to a specific deployment.
     *
     * @param accountIds UUID Strings; {@code null} / empty returns an
     *                   empty success list rather than an error
     */
    RpcResult<List<AuthorizationSnapshotDTO>> batchGetSnapshot(
            Set<String> accountIds);
}