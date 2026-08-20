package com.ulticode.auth.account;

import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.service.AccountQueryService;

import java.util.List;
import java.util.Optional;

/** Auth-owned query port for account reads. */
public interface AuthAccountQueryPort {

    Optional<AuthAccountDTO> findById(String accountId);

    Optional<AuthAccountDTO> findByUsername(String username);

    Optional<AuthAccountDTO> findByEmail(String email);

    List<AuthAccountDTO> queryAccounts(AccountQueryDTO query, int offset, int limit);

    List<AuthAccountDTO> findByIds(java.util.Set<String> accountIds);

    long countByIdsExcludingUsernameMatch(java.util.Set<String> accountIds, String usernameQuery);

    long countAccounts(AccountQueryDTO query);

    AccountQueryService.AccountStatsSummary dashboardStatsSummary(java.time.LocalDateTime todayStart,
                                                                   java.time.LocalDateTime weekStart,
                                                                   java.time.LocalDateTime monthStart);
}
