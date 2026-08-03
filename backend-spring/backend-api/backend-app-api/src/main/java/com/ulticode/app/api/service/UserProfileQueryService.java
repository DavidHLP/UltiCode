package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.common.rpc.RpcResult;

import java.util.List;
import java.util.Set;

/**
 * Provider-owned RPC query service for App-owned user profiles.
 *
 * <p>Offers read-only profile lookups by account ID for Admin projections and
 * consumer services without requiring cross-module SQL joins or entity imports.
 */
public interface UserProfileQueryService {

    RpcResult<UserProfileDTO> getProfileByAccountId(String accountId);

    RpcResult<List<UserProfileDTO>> getProfilesByAccountIds(Set<String> accountIds);
}
