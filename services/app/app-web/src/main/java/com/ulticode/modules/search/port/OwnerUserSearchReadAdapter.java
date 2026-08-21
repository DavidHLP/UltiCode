package com.ulticode.modules.search.port;

import com.ulticode.app.user.port.UserProfileReadMapper;
import com.ulticode.app.user.port.UserProfileReadRow;
import com.ulticode.app.user.port.UserFactView;
import com.ulticode.app.user.port.UserAccountFact;
import com.ulticode.app.user.port.UserFactsReadPort;
import com.ulticode.app.user.port.OwnerUserReadAdapter;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Owner-composed implementation of the user search read port.
 *
 * <p>Auth supplies account identity and lifecycle filtering through its query
 * services; App supplies profile display fields through its local mapper. No
 * App datasource query touches Auth-owned users.
 */
@Component
public class OwnerUserSearchReadAdapter implements UserDirectoryQueryPort {

    private static final int ACCOUNT_PAGE_SIZE = 100;

    private final UserProfileReadMapper profileReadMapper;
    private final UserFactsReadPort userFactsReadPort;

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private AccountQueryService accountQueryService;

    @Autowired
    public OwnerUserSearchReadAdapter(
            UserProfileReadMapper profileReadMapper, UserFactsReadPort userFactsReadPort) {
        this.profileReadMapper = profileReadMapper;
        this.userFactsReadPort = userFactsReadPort;
    }

    /** Test-only convenience constructor; production uses the explicit facts seam. */
    public OwnerUserSearchReadAdapter(UserProfileReadMapper profileReadMapper) {
        this(profileReadMapper, new OwnerUserReadAdapter(profileReadMapper));
    }

    /** Test seam; production injection is supplied by Dubbo. */
    void setAccountQueryService(AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
        if (userFactsReadPort instanceof OwnerUserReadAdapter ownerUserReadAdapter) {
            ownerUserReadAdapter.setAccountQueryService(accountQueryService);
        }
    }

    public List<UserDirectoryRow> search(String query, int limit) {
        return search(query, 0, limit);
    }

    @Override
    public List<UserDirectoryRow> search(String query, int offset, int limit) {
        if (query == null || query.isBlank() || offset < 0 || limit <= 0) {
            return List.of();
        }
        UserSearchCursor cursor = new UserSearchCursor(query);
        cursor.skip(offset);
        List<UserDirectoryRow> rows = new ArrayList<>(limit);
        while (rows.size() < limit) {
            UserDirectoryRow row = cursor.next();
            if (row == null) {
                break;
            }
            rows.add(row);
        }
        return rows;
    }

    @Override
    public long count(String query) {
        if (query == null || query.isBlank()) {
            return 0;
        }
        long total = usernameMatchCount(query);
        long profileTotal = profileReadMapper.countSearchCandidates(query);
        for (long offset = 0; offset < profileTotal;) {
            List<UserProfileReadRow> candidates = profileReadMapper.findSearchCandidates(
                    query, Math.toIntExact(offset), ACCOUNT_PAGE_SIZE);
            if (candidates == null || candidates.isEmpty()) {
                break;
            }
            total += countAccountsByIdsExcludingUsernameMatch(accountIds(candidates), query);
            offset += candidates.size();
            if (candidates.size() < ACCOUNT_PAGE_SIZE) {
                break;
            }
        }
        return total;
    }

    @Override
    public UserDirectoryRow findById(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return null;
        }
        UserFactView fact = userFactsReadPort.findById(accountId);
        if (fact == null) {
            return null;
        }
        return UserDirectoryRow.from(toRow(fact));
    }

    @Override
    public List<UserDirectoryRow> enumerate(int offset, int limit) {
        if (offset < 0 || limit <= 0) {
            return List.of();
        }
        int page = offset / ACCOUNT_PAGE_SIZE + 1;
        int skip = offset % ACCOUNT_PAGE_SIZE;
        List<AuthAccountDTO> pageAccounts = pageItems(queryAccounts(new AccountQueryDTO(
                null, null, null, null, page, ACCOUNT_PAGE_SIZE, "id", "asc", false)));
        if (skip >= pageAccounts.size()) {
            return List.of();
        }
        pageAccounts = pageAccounts.subList(skip, Math.min(skip + limit, pageAccounts.size()));
        Map<String, UserFactView> facts = factsForAccounts(pageAccounts);
        return pageAccounts.stream()
                .map(account -> facts.get(account.accountId()))
                .filter(java.util.Objects::nonNull)
                .map(fact -> UserDirectoryRow.from(toRow(fact)))
                .toList();
    }

    @Override
    public List<UserDirectoryRow> findByIds(Set<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return List.of();
        }
        Map<String, UserFactView> facts = userFactsReadPort.findByIds(accountIds);
        Map<String, UserDirectoryRow> rows = new LinkedHashMap<>();
        for (String accountId : accountIds) {
            UserFactView fact = facts.get(accountId);
            if (fact != null) {
                rows.putIfAbsent(accountId, UserDirectoryRow.from(toRow(fact)));
            }
        }
        return new ArrayList<>(rows.values());
    }

    private long usernameMatchCount(String query) {
        RpcResult<AuthAccountDTO> response = queryAccounts(new AccountQueryDTO(
                query, null, null, null, 1, ACCOUNT_PAGE_SIZE, "id", "asc", true));
        requirePage(response);
        Long total = response.page().total();
        if (total == null) {
            throw unavailable();
        }
        return total;
    }

    private long countAccountsByIdsExcludingUsernameMatch(Set<String> accountIds, String query) {
        if (accountQueryService == null) {
            throw unavailable();
        }
        try {
            RpcResult<Long> response = accountQueryService
                    .countAccountsByIdsExcludingUsernameMatch(accountIds, query);
            requireSuccess(response);
            if (response.data() == null) {
                throw unavailable();
            }
            return response.data();
        } catch (RuntimeException exception) {
            throw unavailable();
        }
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

    private List<AuthAccountDTO> pageItems(RpcResult<AuthAccountDTO> response) {
        requirePage(response);
        List<AuthAccountDTO> accounts = new ArrayList<>();
        for (Object item : response.page().items()) {
            if (!(item instanceof AuthAccountDTO account)) {
                throw unavailable();
            }
            accounts.add(account);
        }
        return accounts;
    }

    private void requirePage(RpcResult<?> response) {
        requireSuccess(response);
        if (response.page() == null || response.page().items() == null) {
            throw unavailable();
        }
    }

    private Set<String> accountIds(List<UserProfileReadRow> rows) {
        Set<String> ids = new LinkedHashSet<>();
        for (UserProfileReadRow row : rows) {
            if (row != null && row.getAccountId() != null && !row.getAccountId().isBlank()) {
                ids.add(row.getAccountId());
            }
        }
        return ids;
    }

    private UserSearchRow toRow(UserFactView fact) {
        UserSearchRow row = new UserSearchRow();
        row.setId(fact.id());
        row.setUsername(fact.username());
        row.setUpdatedAt(fact.authUpdatedAt() != null ? fact.authUpdatedAt() : fact.joinedAt());
        row.setJoinedAt(fact.joinedAt());
        row.setDeletedAt(fact.deletedAt());
        row.setName(fact.name());
        row.setAvatar(fact.avatar());
        row.setProfileUpdatedAt(fact.profileUpdatedAt());
        return row;
    }

    private Map<String, UserFactView> factsForAccounts(List<AuthAccountDTO> accounts) {
        List<UserAccountFact> facts = accounts.stream()
                .map(account -> new UserAccountFact(
                        account.accountId(), account.username(), account.joinedAt(),
                        account.updatedAt(), account.deletedAt(), account.active(), account.banned()))
                .toList();
        return userFactsReadPort.compose(facts);
    }

    private void requireSuccess(RpcResult<?> response) {
        if (response == null || !response.success()) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(BaseErrorCode.UNKNOWN_ERROR, "Auth account query unavailable");
    }

    private final class UserSearchCursor {
        private final PagedUserCursor usernameCursor;
        private final PagedUserCursor profileCursor;
        private UserDirectoryRow usernameHead;
        private UserDirectoryRow profileHead;

        private UserSearchCursor(String query) {
            this.usernameCursor = new UsernameCursor(query);
            this.profileCursor = new ProfileCursor(query);
        }

        private void skip(int offset) {
            for (int i = 0; i < offset && next() != null; i++) {
                // Consume the merged stream without retaining skipped rows.
            }
        }

        private UserDirectoryRow next() {
            if (usernameHead == null) {
                usernameHead = usernameCursor.next();
            }
            if (profileHead == null) {
                profileHead = profileCursor.next();
            }
            if (usernameHead == null && profileHead == null) {
                return null;
            }
            if (usernameHead == null) {
                UserDirectoryRow result = profileHead;
                profileHead = null;
                return result;
            }
            if (profileHead == null) {
                UserDirectoryRow result = usernameHead;
                usernameHead = null;
                return result;
            }
            int comparison = Comparator.nullsLast(String::compareTo).compare(
                    usernameHead.row().getId(), profileHead.row().getId());
            if (comparison == 0) {
                UserDirectoryRow result = usernameHead;
                usernameHead = null;
                profileHead = null;
                return result;
            }
            if (comparison < 0) {
                UserDirectoryRow result = usernameHead;
                usernameHead = null;
                return result;
            }
            UserDirectoryRow result = profileHead;
            profileHead = null;
            return result;
        }
    }

    private abstract class PagedUserCursor {
        private List<UserDirectoryRow> page = List.of();
        private int index;
        private boolean finished;

        private UserDirectoryRow next() {
            while (index >= page.size() && !finished) {
                PageBatch batch = loadPage();
                page = batch.rows();
                index = 0;
                finished = batch.finished();
            }
            return index < page.size() ? page.get(index++) : null;
        }

        protected abstract PageBatch loadPage();
    }

    private final class UsernameCursor extends PagedUserCursor {
        private final String query;
        private int pageNumber = 1;

        private UsernameCursor(String query) {
            this.query = query;
        }

        @Override
        protected PageBatch loadPage() {
            List<AuthAccountDTO> accounts = pageItems(queryAccounts(new AccountQueryDTO(
                    query, null, null, null, pageNumber++, ACCOUNT_PAGE_SIZE, "id", "asc", true)));
            if (accounts.isEmpty()) {
                return new PageBatch(List.of(), true);
            }
            Map<String, UserFactView> facts = factsForAccounts(accounts);
            List<UserDirectoryRow> rows = accounts.stream()
                    .map(account -> facts.get(account.accountId()))
                    .filter(java.util.Objects::nonNull)
                    .map(fact -> UserDirectoryRow.from(toRow(fact)))
                    .toList();
            return new PageBatch(rows, accounts.size() < ACCOUNT_PAGE_SIZE);
        }
    }

    private final class ProfileCursor extends PagedUserCursor {
        private final String query;
        private int offset;

        private ProfileCursor(String query) {
            this.query = query;
        }

        @Override
        protected PageBatch loadPage() {
            List<UserProfileReadRow> candidates = profileReadMapper.findSearchCandidates(
                    query, offset, ACCOUNT_PAGE_SIZE);
            if (candidates == null || candidates.isEmpty()) {
                return new PageBatch(List.of(), true);
            }
            offset += candidates.size();
            Map<String, UserFactView> facts = userFactsReadPort.findByIds(accountIds(candidates));
            List<UserDirectoryRow> rows = candidates.stream()
                    .map(candidate -> facts.get(candidate.getAccountId()))
                    .filter(java.util.Objects::nonNull)
                    .map(fact -> UserDirectoryRow.from(toRow(fact)))
                    .toList();
            return new PageBatch(rows, candidates.size() < ACCOUNT_PAGE_SIZE);
        }
    }

    private record PageBatch(List<UserDirectoryRow> rows, boolean finished) {
    }
}
