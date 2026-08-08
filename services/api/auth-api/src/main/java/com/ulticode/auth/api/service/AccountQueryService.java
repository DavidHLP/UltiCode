package com.ulticode.auth.api.service;

import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * Provider-owned RPC query service for Auth-owned account data.
 *
 * <p>Offers single-row lookup by ID, username, or email, as well as paginated
 * filtering for governance projections. Distinct from {@link IdentityQueryService}
 * which provides minimal identity validation only.
 */
public interface AccountQueryService {

    RpcResult<AuthAccountDTO> getAccountById(String accountId);

    RpcResult<AuthAccountDTO> getAccountByUsername(String username);

    RpcResult<AuthAccountDTO> getAccountByEmail(String email);

    RpcResult<AuthAccountDTO> queryAccounts(AccountQueryDTO query);
}
