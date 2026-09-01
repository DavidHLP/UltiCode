package com.ulticode.auth.api.service;

import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.common.rpc.RpcResult;

import java.util.List;
import java.util.Set;

/**
 * Auth-owned query provider exposing the minimum identity projection
 * required by other modules.
 *
 * <p>Listed in {@code docs/architecture/modules.md} as one of
 * {@code backend-auth}'s Dubbo providers. It exposes minimal
 * identity validation plus the Auth-owned active-recipient query needed by
 * App notification broadcasts.
 *
 * <p>This interface is contract-only; no ServiceImpl lives in this
 * module. The provider implementation belongs to {@code backend-auth}.
 */
public interface IdentityQueryService {

    /**
     * Look up a single identity projection by account id.
     *
     * @param accountId UUID String; must be non-blank
     * @return success with payload, or failure carrying
     *         {@code ACCOUNT_NOT_FOUND} when the id is unknown
     */
    RpcResult<UserIdentityDTO> getIdentity(String accountId);

    /**
     * Batch lookup. Provider must dedupe and return one
     * {@link UserIdentityDTO} per known id; unknown ids are simply
     * omitted from the result list (callers use
     * {@code response.data().size() < ids.size()} as a presence map).
     *
     * <p>Maximum batch size is the provider's choice; the contract
     * does not pin it to avoid coupling to a specific deployment.
     *
     * @param accountIds UUID Strings; {@code null} / empty returns an
     *                   empty success list rather than an error
     */
    RpcResult<List<UserIdentityDTO>> batchGetIdentity(Set<String> accountIds);

    /**
     * Return account ids eligible for an {@code ALL} notification broadcast.
     *
     * <p>The Auth owner applies the authoritative active, non-banned and
     * non-deleted predicates; callers must not reconstruct them from
     * profile data.
     *
     * @return success with eligible account ids
     */
    RpcResult<List<String>> findActiveAccountIds();
}