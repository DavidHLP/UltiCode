package com.ulticode.modules.admin.projection;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.admin.error.AdminReadContract;
import com.ulticode.admin.error.AdminReadContract.OwnerRead;
import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.api.service.UserProfileQueryService;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.port.adapter.CancellableQueryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared helper that enriches admin projections with user display data via
 * Dubbo RPC instead of direct {@code UserMapper} DB access.
 *
 * <p>Merges identity fields (username, role) from {@code IdentityQueryService}
 * (Auth-owned) with profile fields (name, avatar) from
 * {@code UserProfileQueryService} (App-owned) into a unified
 * {@link AdminUserSummary} keyed by account ID.
 *
 * <p>For single-item lookups ({@link #enrichOne}), email is populated via
 * {@code AccountQueryService} since it is only needed in detail views.
 *
 * <p>Follows the same dual-source Dubbo pattern as
 * {@code DefaultAdminUserProjection}. All Dubbo references use
 * {@code check=false} and {@code required=false} so the admin context loads
 * even when providers are down.
 *
 * <p>Degradation is explicit, never silent:
 * <ul>
 *   <li>{@link #enrichWithStatus(Set)} returns an {@link EnrichedUsers}
 *       carrying a {@link DegradationStatus}: {@code OK} when both sources
 *       answered, {@code PARTIAL} when exactly one failed, and
 *       {@code UNAVAILABLE} when both failed.</li>
 *   <li>{@link #enrich(Set)} throws a 503-mapped
 *       {@link BusinessException} ({@link AdminErrorCode#OWNER_QUERY_UNAVAILABLE})
 *       when both sources are unavailable, so infrastructure failure is never
 *       indistinguishable from "no matching users".</li>
 *   <li>Permission failures ({@link AdminErrorCode#FORBIDDEN},
 *       {@link AdminErrorCode#UNAUTHORIZED}) propagate immediately from any
 *       owner RPC path — they are never caught and mapped to
 *       {@code OWNER_QUERY_UNAVAILABLE}.</li>
 * </ul>
 */
@Slf4j
@Component
public class AdminUserEnricher {
    private final CancellableQueryExecutor queryExecutor;

    /** Production construction receives the Admin-owned executor bean. */
    @Autowired
    public AdminUserEnricher(CancellableQueryExecutor queryExecutor) {
        this(null, null, null, queryExecutor);
    }

    AdminUserEnricher(IdentityQueryService identityQueryService,
                      UserProfileQueryService userProfileQueryService,
                      AccountQueryService accountQueryService,
                      CancellableQueryExecutor queryExecutor) {
        this.identityQueryService = identityQueryService;
        this.userProfileQueryService = userProfileQueryService;
        this.accountQueryService = accountQueryService;
        this.queryExecutor = Objects.requireNonNull(queryExecutor, "queryExecutor");
    }

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private IdentityQueryService identityQueryService;

    @Autowired(required = false)
    @DubboReference(group = "backend-app", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private UserProfileQueryService userProfileQueryService;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AccountQueryService accountQueryService;

    /** Query an Auth account page and merge App profiles in one owner-aggregation round. */
    public AccountPage queryAccountsWithProfiles(AccountQueryDTO query) {
        if (accountQueryService == null) {
            throw AdminReadContract.ownerUnavailable("Auth");
        }

        RpcResult<AuthAccountDTO> rpc;
        try {
            rpc = accountQueryService.queryAccounts(query);
        } catch (RuntimeException exception) {
            AdminReadContract.propagate(exception);
            throw AdminReadContract.ownerUnavailable("Auth", exception);
        }
        OwnerRead<AuthAccountDTO> read = AdminReadContract.classify("Auth", rpc);
        if (!read.available() || rpc.page() == null) {
            throw AdminReadContract.ownerUnavailable("Auth");
        }

        RpcResult.Page page = rpc.page();
        @SuppressWarnings("unchecked")
        List<AuthAccountDTO> raw = (List<AuthAccountDTO>) page.items();
        List<AuthAccountDTO> accounts = raw == null
                ? List.of()
                : raw.stream().filter(Objects::nonNull).toList();
        long total = page.total() == null ? 0L : page.total();
        if (accounts.isEmpty()) {
            return new AccountPage(accounts, total, Map.of(), DegradationStatus.OK);
        }

        Set<String> accountIds = accounts.stream()
                .map(AuthAccountDTO::accountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        OwnerRead<Map<String, UserProfileDTO>> profiles = batchProfiles(accountIds);
        Map<String, UserProfileDTO> profileData = profiles.available() && profiles.value() != null
                ? profiles.value() : Collections.emptyMap();
        return new AccountPage(
                accounts,
                total,
                profileData,
                profiles.available() ? DegradationStatus.OK : DegradationStatus.PARTIAL);
    }

    /**
     * Resolve one Auth account without reading optional App facts.
     *
     * <p>This is the authoritative first round of the admin detail query:
     * {@code null} means Auth proved the account is absent, while every
     * transport/provider failure is raised as {@code OWNER_QUERY_UNAVAILABLE}.
     * Permission failures (401/403) propagate immediately.
     */
    public AuthAccountDTO findAccountAuthoritatively(String accountId) {
        if (accountQueryService == null) {
            throw AdminReadContract.ownerUnavailable("Auth");
        }

        RpcResult<AuthAccountDTO> rpc;
        try {
            rpc = accountQueryService.getAccountById(accountId);
        } catch (RuntimeException exception) {
            AdminReadContract.propagate(exception);
            throw AdminReadContract.ownerUnavailable("Auth", exception);
        }
        OwnerRead<AuthAccountDTO> read = AdminReadContract.classify("Auth", rpc);
        if (!read.available()) {
            if (isAuthAccountNotFound(rpc)) {
                return null;
            }
            throw AdminReadContract.ownerUnavailable("Auth");
        }
        if (read.value() == null) {
            throw AdminReadContract.ownerUnavailable("Auth");
        }
        return read.value();
    }

    /**
     * Read one optional App profile through the same bounded profile batch
     * primitive used by list enrichment.
     */
    public ProfileDetail findProfileWithStatus(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return new ProfileDetail(null, DegradationStatus.UNAVAILABLE);
        }
        OwnerRead<Map<String, UserProfileDTO>> profiles = batchProfiles(Set.of(accountId));
        Map<String, UserProfileDTO> profileData = profiles.available() && profiles.value() != null
                ? profiles.value() : Collections.emptyMap();
        return new ProfileDetail(
                profileData.get(accountId),
                profiles.available() ? DegradationStatus.OK : DegradationStatus.UNAVAILABLE);
    }

    public record ProfileDetail(UserProfileDTO profile, DegradationStatus status) {
    }

    /** Query one authoritative Auth account and merge its optional App profile. */
    public AccountDetail findAccountWithProfile(String accountId) {
        AuthAccountDTO account = findAccountAuthoritatively(accountId);
        if (account == null) {
            return null;
        }

        OwnerRead<Map<String, UserProfileDTO>> profiles = batchProfiles(Set.of(accountId));
        Map<String, UserProfileDTO> profileData = profiles.available() && profiles.value() != null
                ? profiles.value() : Collections.emptyMap();
        return new AccountDetail(
                account,
                profileData.get(accountId),
                profiles.available() ? DegradationStatus.OK : DegradationStatus.PARTIAL);
    }

    public record AccountPage(
            List<AuthAccountDTO> accounts,
            long total,
            Map<String, UserProfileDTO> profiles,
            DegradationStatus status) {
    }

    public record AccountDetail(
            AuthAccountDTO account,
            UserProfileDTO profile,
            DegradationStatus status) {
    }

    /**
     * Batch-enrich a set of account IDs.
     *
     * <p>Email is not populated in batch mode (list views don't display it).
     * Use {@link #enrichOne} when email is required.
     *
     * <p>When only one of the two sources is unavailable the best-effort
     * merge is returned; callers that need to surface the partial state to
     * API consumers should call {@link #enrichWithStatus(Set)} instead.
     *
     * @param accountIds IDs to look up; null/empty returns an empty map
     * @return map keyed by accountId; unknown IDs are absent (not null values)
     * @throws BusinessException with
     *         {@link AdminErrorCode#OWNER_QUERY_UNAVAILABLE} when both the
     *         Auth identity and App profile sources are unavailable
     */
    public Map<String, AdminUserSummary> enrich(Set<String> accountIds) {
        EnrichedUsers result = enrichWithStatus(accountIds);
        if (result.status() == DegradationStatus.UNAVAILABLE) {
            log.warn("User identity and profile providers are both unavailable");
            throw AdminReadContract.ownerUnavailable("Auth and App");
        }
        return result.users();
    }

    /**
     * Batch-enrich a set of account IDs and report cross-source degradation
     * explicitly instead of silently returning fewer results.
     *
     * <p>Email is not populated in batch mode (list views don't display it).
     *
     * @param accountIds IDs to look up; null/empty yields an empty map with
     *                   status {@code OK}
     * @return merged summaries keyed by accountId plus the degradation status;
     *         unknown IDs are absent from the map (not mapped to null values)
     */
    public EnrichedUsers enrichWithStatus(Set<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return new EnrichedUsers(Collections.emptyMap(), DegradationStatus.OK);
        }
        return enrichBatchesInParallel(accountIds);
    }

    /**
     * Batch enrichment outcome: the merged summaries plus an explicit
     * {@link DegradationStatus} describing source availability.
     *
     * @param users  merged summaries keyed by accountId (possibly empty)
     * @param status OK / PARTIAL / UNAVAILABLE for this enrichment round
     */
    public record EnrichedUsers(Map<String, AdminUserSummary> users, DegradationStatus status) {
    }

    private EnrichedUsers enrichBatchesInParallel(Set<String> accountIds) {
        CancellableQueryExecutor.Query<OwnerRead<Map<String, UserIdentityDTO>>> identities =
                queryExecutor.submit(() -> batchIdentities(accountIds));
        CancellableQueryExecutor.Query<OwnerRead<Map<String, UserProfileDTO>>> profiles =
                queryExecutor.submit(() -> batchProfiles(accountIds));
        List<OwnerRead<Object>> reads = AdminReadContract.<Object>awaitAndClassify(
                queryExecutor,
                "User enrichment",
                RpcPolicy.QUERY_TIMEOUT_MS,
                TimeUnit.MILLISECONDS,
                identities,
                profiles);
        return mergeBatches(
                accountIds,
                typedRead(reads.get(0)),
                typedRead(reads.get(1)));
    }

    private static EnrichedUsers mergeBatches(
            Set<String> accountIds,
            OwnerRead<Map<String, UserIdentityDTO>> identities,
            OwnerRead<Map<String, UserProfileDTO>> profiles) {
        boolean identitiesAvailable = identities != null
                && identities.available() && identities.value() != null;
        boolean profilesAvailable = profiles != null
                && profiles.available() && profiles.value() != null;
        DegradationStatus status;
        if (!identitiesAvailable && !profilesAvailable) {
            status = DegradationStatus.UNAVAILABLE;
        } else if (!identitiesAvailable || !profilesAvailable) {
            status = DegradationStatus.PARTIAL;
        } else {
            status = DegradationStatus.OK;
        }

        Map<String, UserIdentityDTO> identityMap = !identitiesAvailable
                ? Collections.emptyMap()
                : identities.value();
        Map<String, UserProfileDTO> profileMap = !profilesAvailable
                ? Collections.emptyMap()
                : profiles.value();
        Map<String, AdminUserSummary> users = accountIds.stream()
                .filter(id -> identityMap.containsKey(id) || profileMap.containsKey(id))
                .collect(Collectors.toMap(
                        Function.identity(),
                        id -> {
                            UserIdentityDTO ident = identityMap.get(id);
                            UserProfileDTO profile = profileMap.get(id);
                            return new AdminUserSummary(
                                    id,
                                    ident != null ? ident.username() : null,
                                    ident != null ? ident.role() : null,
                                    profile != null ? profile.name() : null,
                                    profile != null ? profile.avatar() : null,
                                    null
                            );
                        }
                ));
        return new EnrichedUsers(users, status);
    }

    /**
     * Enrich a single account ID.
     *
     * <p>Uses {@code AccountQueryService} to populate email (available in
     * {@code AuthAccountDTO} but not in the minimal identity projection).
     *
     * @return summary, or null if the account is unknown
     * @throws BusinessException with
     *         {@link AdminErrorCode#OWNER_QUERY_UNAVAILABLE} when no source
     *         can answer at all, so callers never mistake an infrastructure
     *         outage for an unknown account
     */
    public AdminUserSummary enrichOne(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return null;
        }

        // Try AccountQueryService first (has email + full account data)
        if (accountQueryService != null) {
            try {
                OwnerRead<AuthAccountDTO> accountRead = AdminReadContract.classify(
                        "Auth", accountQueryService.getAccountById(accountId));
                if (accountRead.available() && accountRead.value() != null) {
                    AuthAccountDTO account = accountRead.value();
                    String name = null;
                    String avatar = null;
                    if (userProfileQueryService != null) {
                        try {
                            OwnerRead<UserProfileDTO> profileRead = AdminReadContract.classify(
                                    "App",
                                    userProfileQueryService.getProfileByAccountId(accountId));
                            if (profileRead.available() && profileRead.value() != null) {
                                name = profileRead.value().name();
                                avatar = profileRead.value().avatar();
                            }
                        } catch (RuntimeException exception) {
                            AdminReadContract.propagate(exception);
                            log.warn("UserProfileQueryService.getProfileByAccountId failed for {}: {}",
                                    accountId, exception.getMessage());
                        }
                    }
                    return new AdminUserSummary(
                            accountId,
                            account.username(),
                            account.role(),
                            name,
                            avatar,
                            account.email());
                }
            } catch (RuntimeException exception) {
                AdminReadContract.propagate(exception);
                log.warn("AccountQueryService.getAccountById failed for {}: {}",
                        accountId, exception.getMessage());
            }
        }

        // Fall back to batch enrichment (no email); UNAVAILABLE there is
        // rethrown so a total outage never surfaces as "account unknown".
        EnrichedUsers result = enrichWithStatus(Set.of(accountId));
        if (result.status() == DegradationStatus.UNAVAILABLE) {
            log.warn("User identity and profile providers are both unavailable");
            throw AdminReadContract.ownerUnavailable("Auth and App");
        }
        return result.users().get(accountId);
    }

    private OwnerRead<Map<String, UserIdentityDTO>> batchIdentities(Set<String> accountIds) {
        if (identityQueryService == null) {
            return OwnerRead.unavailable("Auth owner query unavailable");
        }
        RpcResult<List<UserIdentityDTO>> rpc = identityQueryService.batchGetIdentity(accountIds);
        OwnerRead<List<UserIdentityDTO>> read = AdminReadContract.classify("Auth", rpc);
        if (!read.available() || read.value() == null) {
            return OwnerRead.unavailable(read.reason());
        }
        return OwnerRead.available(read.value().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserIdentityDTO::accountId, Function.identity(), (a, b) -> a)));
    }

    private OwnerRead<Map<String, UserProfileDTO>> batchProfiles(Set<String> accountIds) {
        if (userProfileQueryService == null) {
            return OwnerRead.unavailable("App owner query unavailable");
        }
        try {
            RpcResult<List<UserProfileDTO>> rpc = userProfileQueryService.getProfilesByAccountIds(accountIds);
            OwnerRead<List<UserProfileDTO>> read = AdminReadContract.classify("App", rpc);
            if (!read.available() || read.value() == null) {
                return OwnerRead.unavailable(read.reason());
            }
            return OwnerRead.available(read.value().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(UserProfileDTO::accountId, Function.identity(), (a, b) -> a)));
        } catch (RuntimeException exception) {
            AdminReadContract.propagate(exception);
            log.warn("UserProfileQueryService.getProfilesByAccountIds failed for {} ids: {}",
                    accountIds.size(), exception.getMessage());
            return OwnerRead.unavailable("App owner query unavailable");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> OwnerRead<T> typedRead(OwnerRead<?> read) {
        return (OwnerRead<T>) read;
    }

    private static boolean isAuthAccountNotFound(RpcResult<?> rpc) {
        if (rpc == null) {
            return false;
        }
        RpcResult.ErrorPayload error = rpc.error();
        return error != null
                && AuthErrorCode.NAMESPACE.equals(error.namespace())
                && error.code() == AuthErrorCode.ACCOUNT_NOT_FOUND.code();
    }
}
