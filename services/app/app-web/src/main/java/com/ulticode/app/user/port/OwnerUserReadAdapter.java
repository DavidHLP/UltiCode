package com.ulticode.app.user.port;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OwnerUserReadAdapter implements UserReadMapper {

    private final UserProfileReadMapper profileReadMapper;

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private AccountQueryService accountQueryService;

    public OwnerUserReadAdapter(UserProfileReadMapper profileReadMapper) {
        this.profileReadMapper = profileReadMapper;
    }

    void setAccountQueryService(AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
    }

    @Override
    public UserSummaryView selectById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        AuthAccountDTO account = accountOrNull(accountQueryService().getAccountById(id));
        return account == null ? null : compose(account, profileReadMapper.findByAccountId(account.accountId()));
    }

    @Override
    public UserSummaryView selectByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        AuthAccountDTO account = accountOrNull(accountQueryService().getAccountByUsername(username));
        return account == null ? null : compose(account, profileReadMapper.findByAccountId(account.accountId()));
    }

    @Override
    public UserSummaryView selectByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        AuthAccountDTO account = accountOrNull(accountQueryService().getAccountByEmail(email));
        return account == null ? null : compose(account, profileReadMapper.findByAccountId(account.accountId()));
    }

    @Override
    public List<UserSummaryView> selectActiveUsers(int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            return List.of();
        }
        int page = offset / limit + 1;
        RpcResult<AuthAccountDTO> response = accountQueryService().queryAccounts(
                new AccountQueryDTO(null, null, true, false, page, limit, "joinedAt", "desc"));
        List<AuthAccountDTO> accounts = pageItems(response);
        if (accounts.isEmpty()) {
            return List.of();
        }
        Map<String, UserProfileDTO> profiles = profileReadMapper.findByAccountIds(
                        accounts.stream().map(AuthAccountDTO::accountId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(UserProfileDTO::accountId, profile -> profile));
        return accounts.stream().map(account -> compose(account, profiles.get(account.accountId()))).toList();
    }

    @Override
    public long countActiveUsers() {
        RpcResult<AuthAccountDTO> response = accountQueryService().queryAccounts(
                new AccountQueryDTO(null, null, true, false, 1, 1, "joinedAt", "desc"));
        requireSuccess(response);
        if (response.page() == null || response.page().total() == null) {
            throw unavailable();
        }
        return response.page().total();
    }

    @Override
    public int countById(String id) {
        return selectById(id) == null ? 0 : 1;
    }

    private AccountQueryService accountQueryService() {
        if (accountQueryService == null) {
            throw unavailable();
        }
        return accountQueryService;
    }

    private AuthAccountDTO accountOrNull(RpcResult<AuthAccountDTO> response) {
        if (response != null && !response.success() && response.error() != null
                && AuthErrorCode.NAMESPACE.equals(response.error().namespace())
                && response.error().code() == AuthErrorCode.ACCOUNT_NOT_FOUND.code()) {
            return null;
        }
        requireSuccess(response);
        return response.data();
    }

    private List<AuthAccountDTO> pageItems(RpcResult<AuthAccountDTO> response) {
        requireSuccess(response);
        if (response.page() == null || response.page().items() == null) {
            throw unavailable();
        }
        List<AuthAccountDTO> accounts = response.page().items().stream()
                .filter(AuthAccountDTO.class::isInstance)
                .map(AuthAccountDTO.class::cast)
                .toList();
        if (accounts.size() != response.page().items().size()) {
            throw unavailable();
        }
        return accounts;
    }

    private void requireSuccess(RpcResult<?> response) {
        if (response == null || !response.success()) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(BaseErrorCode.UNKNOWN_ERROR, "Auth account query unavailable");
    }

    private UserSummaryView compose(AuthAccountDTO account, UserProfileDTO profile) {
        UserProfileDTO resolved = profile != null ? profile : UserProfileDTO.empty(account.accountId());
        return new UserSummaryView(
                account.accountId(), account.username(), resolved.name(), account.email(), resolved.avatar(),
                resolved.bio(), resolved.company(), resolved.github(), account.joinedAt(), resolved.location(),
                resolved.twitter(), resolved.website(), resolved.preferredLanguage(), account.role(),
                account.active(), account.banned(), account.lastLoginAt());
    }
}
