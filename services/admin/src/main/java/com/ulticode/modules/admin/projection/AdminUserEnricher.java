package com.ulticode.modules.admin.projection;

import com.ulticode.admin.error.AdminErrorCode;
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
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
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
 * </ul>
 */
@Slf4j
@Component
public class AdminUserEnricher {
    private static final int BATCH_QUERY_POOL_SIZE = 2;
    private static final int BATCH_QUERY_QUEUE_CAPACITY = 2;
    private static final String BATCH_QUERY_THREAD_PREFIX = "admin-user-enrichment-query";

    private final ThreadPoolExecutor queryExecutor;

    public AdminUserEnricher() {
        this(null, null, null);
    }

    AdminUserEnricher(IdentityQueryService identityQueryService,
                      UserProfileQueryService userProfileQueryService,
                      AccountQueryService accountQueryService) {
        this(identityQueryService, userProfileQueryService, accountQueryService,
                newQueryExecutor());
    }

    AdminUserEnricher(IdentityQueryService identityQueryService,
                      UserProfileQueryService userProfileQueryService,
                      AccountQueryService accountQueryService,
                      ThreadPoolExecutor queryExecutor) {
        this.identityQueryService = identityQueryService;
        this.userProfileQueryService = userProfileQueryService;
        this.accountQueryService = accountQueryService;
        this.queryExecutor = Objects.requireNonNull(queryExecutor, "queryExecutor");
    }

    @PreDestroy
    void shutdownQueryExecutor() {
        queryExecutor.shutdownNow();
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

    /**
     * Resolve one Auth account without reading optional App facts.
     *
     * <p>This is the authoritative first round of the admin detail query:
     * {@code null} means Auth proved the account is absent, while every
     * transport/provider failure is raised as {@code OWNER_QUERY_UNAVAILABLE}.
     */
    public AuthAccountDTO findAccountAuthoritatively(String accountId) {
        if (accountQueryService == null) {
            throw ownerUnavailable("AccountQueryService is unavailable");
        }

        RpcResult<AuthAccountDTO> rpc;
        try {
            rpc = accountQueryService.getAccountById(accountId);
        } catch (Exception exception) {
            log.warn("AccountQueryService.getAccountById failed for {}: {}",
                    accountId, exception.getClass().getSimpleName());
            throw ownerUnavailable("AccountQueryService.getAccountById failed", exception);
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
            throw ownerUnavailable("AccountQueryService.getAccountById returned empty payload");
        }
        return rpc.data();
    }

    /**
     * Read one optional App profile through the same bounded profile batch
     * primitive used by list enrichment.
     */
    public ProfileDetail findProfileWithStatus(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return new ProfileDetail(null, DegradationStatus.UNAVAILABLE);
        }
        ProfileBatch profiles = batchProfiles(Set.of(accountId));
        return new ProfileDetail(
                profiles.data().get(accountId),
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

        ProfileBatch profiles = batchProfiles(Set.of(accountId));
        return new AccountDetail(
                account,
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

    private record IdentityBatch(Map<String, UserIdentityDTO> data, boolean available) {
    }

    private record ProfileBatch(Map<String, UserProfileDTO> data, boolean available) {
    }

    private EnrichedUsers enrichBatchesInParallel(Set<String> accountIds) {
        EnrichmentQuery<IdentityBatch> identities =
                submitQuery(() -> batchIdentities(accountIds));
        EnrichmentQuery<ProfileBatch> profiles =
                submitQuery(() -> batchProfiles(accountIds));
        try {
            CompletableFuture.allOf(identities.result(), profiles.result())
                    .get(RpcPolicy.QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cancel(identities, profiles);
        } catch (ExecutionException exception) {
            cancel(identities, profiles);
            rethrowFatal(exception.getCause());
        } catch (TimeoutException exception) {
            cancel(identities, profiles);
        }
        return mergeBatches(
                accountIds,
                completedResult(identities.result()),
                completedResult(profiles.result()));
    }

    private <T> EnrichmentQuery<T> submitQuery(Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Future<?> execution;
        try {
            execution = queryExecutor.submit(() -> {
                try {
                    result.complete(task.call());
                } catch (Error error) {
                    result.completeExceptionally(error);
                    throw error;
                } catch (Exception exception) {
                    result.completeExceptionally(exception);
                }
            });
        } catch (RejectedExecutionException rejected) {
            result.completeExceptionally(rejected);
            execution = null;
        }
        return new EnrichmentQuery<>(result, execution);
    }

    @SafeVarargs
    private static void cancel(EnrichmentQuery<?>... queries) {
        for (EnrichmentQuery<?> query : queries) {
            // Cancel the public result before interrupting its worker, matching
            // the admin query executor's race-safe cancellation order.
            query.result().cancel(true);
            if (query.execution() != null) {
                query.execution().cancel(true);
            }
        }
    }

    private static <T> T completedResult(CompletableFuture<T> result) {
        if (!result.isDone() || result.isCancelled()) {
            return null;
        }
        try {
            return result.join();
        } catch (CompletionException exception) {
            rethrowFatal(exception.getCause());
            return null;
        }
    }

    private static void rethrowFatal(Throwable cause) {
        if (cause instanceof Error error) {
            throw error;
        }
    }

    private static EnrichedUsers mergeBatches(
            Set<String> accountIds, IdentityBatch identities, ProfileBatch profiles) {
        boolean identitiesAvailable = identities != null && identities.available();
        boolean profilesAvailable = profiles != null && profiles.available();
        DegradationStatus status;
        if (!identitiesAvailable && !profilesAvailable) {
            status = DegradationStatus.UNAVAILABLE;
        } else if (!identitiesAvailable || !profilesAvailable) {
            status = DegradationStatus.PARTIAL;
        } else {
            status = DegradationStatus.OK;
        }

        Map<String, UserIdentityDTO> identityMap = identities == null || identities.data() == null
                ? Collections.emptyMap()
                : identities.data();
        Map<String, UserProfileDTO> profileMap = profiles == null || profiles.data() == null
                ? Collections.emptyMap()
                : profiles.data();
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

    private static ThreadPoolExecutor newQueryExecutor() {
        return new ThreadPoolExecutor(
                BATCH_QUERY_POOL_SIZE,
                BATCH_QUERY_POOL_SIZE,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(BATCH_QUERY_QUEUE_CAPACITY),
                new NamedDaemonThreadFactory(BATCH_QUERY_THREAD_PREFIX),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private record EnrichmentQuery<T>(CompletableFuture<T> result, Future<?> execution) {
    }

    private static final class NamedDaemonThreadFactory implements ThreadFactory {
        private final String name;
        private final AtomicInteger sequence = new AtomicInteger();

        private NamedDaemonThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, name + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
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
