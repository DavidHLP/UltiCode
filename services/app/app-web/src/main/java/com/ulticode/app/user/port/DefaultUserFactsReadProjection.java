package com.ulticode.app.user.port;

import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * Owner-composed User Facts View projection.
 *
 * <p>This is the single read seam for the Auth account + App profile fact
 * composition used by Search and other cross-owner readers. It owns batching,
 * missing-account semantics and fail-closed owner-unavailable behavior; the
 * callers do not assemble two owner reads themselves.</p>
 */
@Component
public class DefaultUserFactsReadProjection
        implements UserFactsProjection {

    private final UserProfileReadMapper profileReadMapper;

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AccountQueryService accountQueryService;

    public DefaultUserFactsReadProjection(UserProfileReadMapper profileReadMapper) {
        this.profileReadMapper = profileReadMapper;
    }

    /** Test seam for injecting a deterministic Auth owner adapter. */
    public void setAccountQueryService(AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
    }

    @Override
    public UserFactView findById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        AuthAccountDTO account;
        account = ownerCall(() -> accountOrNull(accountQueryService().getAccountById(id)));
        return account == null ? null : toFact(account, searchProfileById(account.accountId()));
    }

    @Override
    public Map<String, UserFactView> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<String> requested = ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (requested.isEmpty()) {
            return Map.of();
        }
        RpcResult<List<AuthAccountDTO>> response;
        response = ownerCall(() -> accountQueryService().getAccountsByIds(requested));
        requireSuccess(response);
        if (response.data() == null) {
            throw unavailable();
        }
        List<UserProfileReadRow> profileRows = ownerCall(
                () -> profileReadMapper.findSearchRowsByAccountIds(requested));
        if (profileRows == null) {
            profileRows = List.of();
        }
        Map<String, UserProfileReadRow> profiles = profileRows.stream()
                .filter(profile -> profile != null && requested.contains(profile.getAccountId()))
                .collect(Collectors.toMap(UserProfileReadRow::getAccountId, profile -> profile,
                        (first, ignored) -> first));
        Map<String, AuthAccountDTO> accounts = response.data().stream()
                .filter(account -> account != null && requested.contains(account.accountId()))
                .collect(Collectors.toMap(AuthAccountDTO::accountId, account -> account,
                        (first, ignored) -> first));
        Map<String, UserFactView> result = new LinkedHashMap<>();
        for (String accountId : requested) {
            AuthAccountDTO account = accounts.get(accountId);
            if (account != null) {
                result.put(accountId, toFact(account, profiles.get(accountId)));
            }
        }
        return result;
    }

    @Override
    public Map<String, UserFactView> compose(Collection<UserAccountFact> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return Map.of();
        }
        Map<String, UserAccountFact> requested = new LinkedHashMap<>();
        for (UserAccountFact account : accounts) {
            if (account != null && account.id() != null && !account.id().isBlank()) {
                requested.putIfAbsent(account.id(), account);
            }
        }
        if (requested.isEmpty()) {
            return Map.of();
        }
        List<UserProfileReadRow> profileRows = ownerCall(
                () -> profileReadMapper.findSearchRowsByAccountIds(requested.keySet()));
        if (profileRows == null) {
            profileRows = List.of();
        }
        Map<String, UserProfileReadRow> profiles = profileRows.stream()
                .filter(profile -> profile != null && requested.containsKey(profile.getAccountId()))
                .collect(Collectors.toMap(UserProfileReadRow::getAccountId, profile -> profile,
                        (first, ignored) -> first));
        Map<String, UserFactView> result = new LinkedHashMap<>();
        requested.forEach((id, account) -> result.put(id, toFact(account, profiles.get(id))));
        return result;
    }

    private UserProfileReadRow searchProfileById(String accountId) {
        List<UserProfileReadRow> rows = ownerCall(
                () -> profileReadMapper.findSearchRowsByAccountIds(Set.of(accountId)));
        return rows == null ? null : rows.stream().findFirst().orElse(null);
    }

    private UserFactView toFact(AuthAccountDTO account, UserProfileReadRow profile) {
        return toFact(new UserAccountFact(
                account.accountId(), account.username(), account.joinedAt(),
                account.updatedAt(), account.deletedAt(), account.active(), account.banned()), profile);
    }

    private UserFactView toFact(UserAccountFact account, UserProfileReadRow profile) {
        return new UserFactView(
                account.id(), account.username(),
                profile == null ? null : profile.getName(),
                profile == null ? null : profile.getAvatar(),
                account.joinedAt(), account.authUpdatedAt(),
                profile == null ? null : profile.getUpdatedAt(),
                account.deletedAt(), account.active(), account.banned());
    }

    private AccountQueryService accountQueryService() {
        if (accountQueryService == null) {
            throw unavailable();
        }
        return accountQueryService;
    }

    private <T> T ownerCall(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
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

    private void requireSuccess(RpcResult<?> response) {
        if (response == null || !response.success()) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(BaseErrorCode.UNKNOWN_ERROR, "Auth account query unavailable");
    }
}
