package com.ulticode.modules.search.port;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.user.port.UserProfileReadMapper;
import com.ulticode.app.user.port.UserProfileReadRow;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * Owner-composed implementation of the user search read port.
 *
 * <p>Auth supplies account identity and lifecycle filtering through its query
 * services; App supplies profile display fields through its local mapper. No
 * App datasource query touches Auth-owned {@code users}.
 */
@Component
public class OwnerUserSearchReadAdapter implements UserSearchReadMapper {

    private static final int ACCOUNT_PAGE_SIZE = 100;

    private final UserProfileReadMapper profileReadMapper;

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private AccountQueryService accountQueryService;

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private IdentityQueryService identityQueryService;

    public OwnerUserSearchReadAdapter(UserProfileReadMapper profileReadMapper) {
        this.profileReadMapper = profileReadMapper;
    }

    @Override
    public List<UserSearchRow> searchIndex(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        RpcResult<AuthAccountDTO> usernameResponse = queryAccounts(new AccountQueryDTO(
                query, null, null, null, 1, Math.min(limit, ACCOUNT_PAGE_SIZE),
                "username", "asc", true));
        Map<String, AuthAccountDTO> usernameAccounts = new LinkedHashMap<>();
        for (AuthAccountDTO account : pageItems(usernameResponse)) {
            if (containsIgnoreCase(account.username(), query)) {
                usernameAccounts.put(account.accountId(), account);
            }
        }

        List<UserProfileReadRow> nameMatches = profileSearchCandidates(query, Math.min(limit, ACCOUNT_PAGE_SIZE));
        Map<String, UserProfileReadRow> profiles = profileMap(nameMatches);
        Set<String> profileIds = new LinkedHashSet<>(profiles.keySet());
        profileIds.removeAll(usernameAccounts.keySet());
        Map<String, UserIdentityDTO> identities = batchIdentities(profileIds);

        Set<String> accountIds = new LinkedHashSet<>(usernameAccounts.keySet());
        accountIds.addAll(identities.keySet());
        profiles.putAll(profileMap(profileRows(accountIds)));

        Map<String, UserSearchRow> rows = new LinkedHashMap<>();
        usernameAccounts.values().forEach(account -> rows.put(
                account.accountId(), toRow(account, profiles.get(account.accountId()))));
        identities.values().forEach(identity -> rows.put(
                identity.accountId(), toRow(identity, profiles.get(identity.accountId()))));

        return rows.values().stream()
                .sorted(rowComparator())
                .limit(limit)
                .toList();

    }
    @Override
    public UserSearchRow findIndexRowById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        AuthAccountDTO account = accountOrNull(accountById(id));
        if (account == null) {
            return null;
        }
        UserProfileDTO profile = profileReadMapper.findByAccountId(account.accountId());
        UserProfileReadRow searchProfile = toSearchProfile(profile);
        return toRow(account, searchProfile);
    }

    @Override
    public List<UserSearchRow> enumerateIndex(int offset, int limit) {
        if (offset < 0 || limit <= 0) {
            return List.of();
        }
        int page = offset / ACCOUNT_PAGE_SIZE + 1;
        int skip = offset % ACCOUNT_PAGE_SIZE;
        RpcResult<AuthAccountDTO> response = queryAccounts(new AccountQueryDTO(
                null, null, null, null, page, ACCOUNT_PAGE_SIZE, "id", "asc", false));
        List<AuthAccountDTO> pageItems = pageItems(response);
        if (skip >= pageItems.size()) {
            return List.of();
        }
        List<AuthAccountDTO> pageAccounts =
                pageItems.subList(skip, Math.min(skip + limit, pageItems.size()));
        Set<String> accountIds = pageAccounts.stream().map(AuthAccountDTO::accountId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, UserProfileReadRow> profiles = profileMap(profileRows(accountIds));
        return pageAccounts.stream()
                .map(account -> toRow(account, profiles.get(account.accountId())))
                .toList();
    }
    /** Test seam; production injection is supplied by Dubbo. */
    void setAccountQueryService(AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
    }

    /** Test seam; production injection is supplied by Dubbo. */
    void setIdentityQueryService(IdentityQueryService identityQueryService) {
        this.identityQueryService = identityQueryService;
    }

    private RpcResult<AuthAccountDTO> queryAccounts(AccountQueryDTO query) {
        if (accountQueryService == null) {
            throw unavailable();
        }
        try {
            return accountQueryService.queryAccounts(query);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }


    private RpcResult<AuthAccountDTO> accountById(String id) {
        if (accountQueryService == null) {
            throw unavailable();
        }
        try {
            return accountQueryService.getAccountById(id);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private List<AuthAccountDTO> pageItems(RpcResult<AuthAccountDTO> response) {
        requireSuccess(response);
        if (response.page() == null || response.page().items() == null) {
            throw unavailable();
        }
        List<AuthAccountDTO> accounts = new ArrayList<>();
        for (Object item : response.page().items()) {
            if (!(item instanceof AuthAccountDTO account)) {
                throw unavailable();
            }
            accounts.add(account);
        }
        return accounts;
    }

    private AuthAccountDTO accountOrNull(RpcResult<AuthAccountDTO> response) {
        if (response != null && !response.success() && response.error() != null
                && AuthErrorCode.NAMESPACE.equals(response.error().namespace())
                && response.error().code() == AuthErrorCode.ACCOUNT_NOT_FOUND.code()) {
            return null;
        }
        requireSuccess(response);
        if (response.data() == null) {
            throw unavailable();
        }
        return response.data();
    }

    private Map<String, UserIdentityDTO> batchIdentities(Set<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        if (identityQueryService == null) {
            throw unavailable();
        }
        RpcResult<List<UserIdentityDTO>> response;
        try {
            response = identityQueryService.batchGetIdentity(ids);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
        if (response == null || !response.success() || response.data() == null) {
            throw unavailable();
        }
        Map<String, UserIdentityDTO> identities = new LinkedHashMap<>();
        for (UserIdentityDTO identity : response.data()) {
            if (identity != null && ids.contains(identity.accountId())) {
                identities.putIfAbsent(identity.accountId(), identity);
            }
        }
        return identities;
    }

    private List<UserProfileReadRow> profileSearchCandidates(String query, int limit) {
        List<UserProfileReadRow> rows = profileReadMapper.findSearchCandidatesBounded(query, limit);
        return rows == null ? List.of() : rows;
    }
    private List<UserProfileReadRow> profileRows(Set<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<UserProfileReadRow> rows = profileReadMapper.findSearchRowsByAccountIds(ids);
        return rows == null ? List.of() : rows;
    }

    private Map<String, UserProfileReadRow> profileMap(List<UserProfileReadRow> rows) {
        Map<String, UserProfileReadRow> profiles = new HashMap<>();
        for (UserProfileReadRow row : rows) {
            if (row != null && row.getAccountId() != null && !row.getAccountId().isBlank()) {
                profiles.putIfAbsent(row.getAccountId(), row);
            }
        }
        return profiles;
    }

    private UserProfileReadRow toSearchProfile(UserProfileDTO profile) {
        if (profile == null) {
            return null;
        }
        UserProfileReadRow row = new UserProfileReadRow();
        row.setAccountId(profile.accountId());
        row.setName(profile.name());
        row.setAvatar(profile.avatar());
        return row;
    }

    private UserSearchRow toRow(AuthAccountDTO account, UserProfileReadRow profile) {
        UserSearchRow row = new UserSearchRow();
        row.setId(account.accountId());
        row.setUsername(account.username());
        LocalDateTime authUpdated = account.updatedAt() != null ? account.updatedAt() : account.joinedAt();
        LocalDateTime profileUpdated = profile != null ? profile.getUpdatedAt() : null;
        LocalDateTime watermark = authUpdated;
        if (profileUpdated != null && (watermark == null || profileUpdated.isAfter(watermark))) {
            watermark = profileUpdated;
        }
        row.setUpdatedAt(watermark);
        row.setJoinedAt(account.joinedAt());
        row.setDeletedAt(account.deletedAt());
        applyProfile(row, profile);
        return row;
    }

    private UserSearchRow toRow(UserIdentityDTO identity, UserProfileReadRow profile) {
        UserSearchRow row = new UserSearchRow();
        row.setId(identity.accountId());
        row.setUsername(identity.username());
        applyProfile(row, profile);
        return row;
    }

    private void applyProfile(UserSearchRow row, UserProfileReadRow profile) {
        if (profile == null) {
            return;
        }
        row.setName(profile.getName());
        row.setAvatar(profile.getAvatar());
        row.setProfileUpdatedAt(profile.getUpdatedAt());
    }

    private Comparator<UserSearchRow> rowComparator() {
        return Comparator.comparing(
                        UserSearchRow::getUsername,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(UserSearchRow::getUsername, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(UserSearchRow::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
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
