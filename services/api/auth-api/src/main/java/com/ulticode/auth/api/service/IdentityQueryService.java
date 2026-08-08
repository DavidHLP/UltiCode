package com.ulticode.auth.api.service;

import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.common.rpc.RpcResult;

import java.util.List;
import java.util.Set;

/**
 * Auth-owned query provider exposing the minimum identity projection
 * required by other modules.
 *
 * <p>Listed in {@code docs/MICROSERVICE_MIGRATION_GUIDE.md} &sect;4.1
 * as one of {@code backend-auth}'s three Dubbo providers. Per &sect;6.2
 * the interface signature mirrors the migration guide example
 * exactly: one-by-one and batch reads only; batch takes precedence
 * to avoid N+1 fan-out from the Admin dashboard and App enrichments.
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
}