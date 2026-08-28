package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.api.service.UserProfileQueryService;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.DegradationStatus;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.ulticode.common.rpc.RpcPolicy;

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
 * </ul>
 */
@Slf4j
@Component
public class AdminUserEnricher {

    public AdminUserEnricher() {
    }

    AdminUserEnricher(IdentityQueryService identityQueryService,
                      UserProfileQueryService userProfileQueryService,
                      AccountQueryService accountQueryService) {
        this.identityQueryService = identityQueryService;
        this.userProfileQueryService = userProfileQueryService;
        this.accountQueryService = accountQueryService;
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
            throw ownerUnavailable("AccountQueryService is unavailable");
        }

        RpcResult<AuthAccountDTO> rpc;
        try {
            rpc = accountQueryService.queryAccounts(query);
        } catch (Exception e) {
            log.warn("AccountQueryService.queryAccounts failed: {}", e.getMessage());
            throw ownerUnavailable("AccountQueryService.queryAccounts failed", e);
        }
        if (rpc == null || !rpc.success() || rpc.page() == null) {
            throw ownerUnavailable("AccountQueryService.queryAccounts returned failure");
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
        ProfileBatch profiles = batchProfiles(accountIds);
        return new AccountPage(
                accounts,
                total,
                profiles.data(),
                profiles.available() ? DegradationStatus.OK : DegradationStatus.PARTIAL);
    }

    /** Query one authoritative Auth account and merge its optional App profile. */
    public AccountDetail findAccountWithProfile(String accountId) {
        if (accountQueryService == null) {
            throw ownerUnavailable("AccountQueryService is unavailable");
        }

        RpcResult<AuthAccountDTO> rpc;
        try {
            rpc = accountQueryService.getAccountById(accountId);
        } catch (Exception e) {
            log.warn("AccountQueryService.getAccountById failed for {}: {}", accountId, e.getMessage());
            throw ownerUnavailable("AccountQueryService.getAccountById failed", e);
        }
        if (rpc == null) {
            throw ownerUnavailable("AccountQueryService.getAccountById returned null");
        }
        if (!rpc.success()) {
            if (isAuthAccountNotFound(rpc)) {
                return null;
            }
            throw ownerUnavailable("AccountQueryService.getAccountById returned failure");
        }
        if (rpc.data() == null) {
            return null;
        }

        ProfileBatch profiles = batchProfiles(Set.of(accountId));
        return new AccountDetail(
                rpc.data(),
                profiles.data().get(accountId),
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
            throw new BusinessException(AdminErrorCode.OWNER_QUERY_UNAVAILABLE);
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

        IdentityBatch identities = batchIdentities(accountIds);
        ProfileBatch profiles = batchProfiles(accountIds);

        DegradationStatus status;
        if (!identities.available() && !profiles.available()) {
            status = DegradationStatus.UNAVAILABLE;
        } else if (!identities.available() || !profiles.available()) {
            status = DegradationStatus.PARTIAL;
        } else {
            status = DegradationStatus.OK;
        }

        Map<String, UserIdentityDTO> identityMap = identities.data();
        Map<String, UserProfileDTO> profileMap = profiles.data();
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
     * Batch enrichment outcome: the merged summaries plus an explicit
     * {@link DegradationStatus} describing source availability.
     *
     * @param users  merged summaries keyed by accountId (possibly empty)
     * @param status OK / PARTIAL / UNAVAILABLE for this enrichment round
     */
    public record EnrichedUsers(Map<String, AdminUserSummary> users, DegradationStatus status) {
    }

    private record IdentityBatch(Map<String, UserIdentityDTO> data, boolean available) {
    }

    private record ProfileBatch(Map<String, UserProfileDTO> data, boolean available) {
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
            RpcResult<AuthAccountDTO> rpc = null;
            try {
                rpc = accountQueryService.getAccountById(accountId);
            } catch (Exception e) {
                log.warn("AccountQueryService.getAccountById failed for {}: {}", accountId, e.getMessage());
            }
            if (rpc != null && rpc.success() && rpc.data() != null) {
                    AuthAccountDTO account = rpc.data();
                    // Profile fields from UserProfileQueryService
                    String name = null;
                    String avatar = null;
                    if (userProfileQueryService != null) {
                        try {
                            RpcResult<UserProfileDTO> profileRpc = userProfileQueryService.getProfileByAccountId(accountId);
                            if (profileRpc != null && profileRpc.success() && profileRpc.data() != null) {
                                name = profileRpc.data().name();
                                avatar = profileRpc.data().avatar();
                            }
                        } catch (Exception e) {
                            log.warn("UserProfileQueryService.getProfileByAccountId failed for {}: {}", accountId, e.getMessage());
                        }
                    }
                    return new AdminUserSummary(
                            accountId,
                            account.username(),
                            account.role(),
                            name,
                            avatar,
                            account.email()
                    );
                }
        }

        // Fall back to batch enrichment (no email); UNAVAILABLE there is
        // rethrown so a total outage never surfaces as "account unknown".
        EnrichedUsers result = enrichWithStatus(Set.of(accountId));
        if (result.status() == DegradationStatus.UNAVAILABLE) {
            log.warn("User identity and profile providers are both unavailable");
            throw new BusinessException(AdminErrorCode.OWNER_QUERY_UNAVAILABLE);
        }
        return result.users().get(accountId);
    }

    private IdentityBatch batchIdentities(Set<String> accountIds) {
        if (identityQueryService == null) {
            return new IdentityBatch(Collections.emptyMap(), false);
        }
        try {
            RpcResult<List<UserIdentityDTO>> rpc = identityQueryService.batchGetIdentity(accountIds);
            if (rpc == null || !rpc.success() || rpc.data() == null) {
                return new IdentityBatch(Collections.emptyMap(), false);
            }
            return new IdentityBatch(rpc.data().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(UserIdentityDTO::accountId, Function.identity(), (a, b) -> a)),
                    true);
        } catch (Exception e) {
            log.warn("IdentityQueryService.batchGetIdentity failed for {} ids: {}", accountIds.size(), e.getMessage());
            return new IdentityBatch(Collections.emptyMap(), false);
        }
    }

    private ProfileBatch batchProfiles(Set<String> accountIds) {
        if (userProfileQueryService == null) {
            return new ProfileBatch(Collections.emptyMap(), false);
        }
        try {
            RpcResult<List<UserProfileDTO>> rpc = userProfileQueryService.getProfilesByAccountIds(accountIds);
            if (rpc == null || !rpc.success() || rpc.data() == null) {
                return new ProfileBatch(Collections.emptyMap(), false);
            }
            return new ProfileBatch(rpc.data().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(UserProfileDTO::accountId, Function.identity(), (a, b) -> a)),
                    true);
        } catch (Exception e) {
            log.warn("UserProfileQueryService.getProfilesByAccountIds failed for {} ids: {}", accountIds.size(), e.getMessage());
            return new ProfileBatch(Collections.emptyMap(), false);
        }
    }

    private static boolean isAuthAccountNotFound(RpcResult<?> rpc) {
        RpcResult.ErrorPayload error = rpc.error();
        return error != null
                && AuthErrorCode.NAMESPACE.equals(error.namespace())
                && error.code() == AuthErrorCode.ACCOUNT_NOT_FOUND.code();
    }

    private static BusinessException ownerUnavailable(String message) {
        log.warn("Owner query unavailable: {}", message);
        return new BusinessException(AdminErrorCode.OWNER_QUERY_UNAVAILABLE);
    }

    private static BusinessException ownerUnavailable(String message, Throwable cause) {
        log.warn("Owner query unavailable: {}", message, cause);
        return new BusinessException(AdminErrorCode.OWNER_QUERY_UNAVAILABLE, cause);
    }
}
