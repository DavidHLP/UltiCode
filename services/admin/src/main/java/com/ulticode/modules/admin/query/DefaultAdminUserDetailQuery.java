package com.ulticode.modules.admin.query;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.admin.error.AdminReadContract;
import com.ulticode.admin.error.AdminReadContract.OwnerRead;
import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.api.service.SolutionReadPort;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.dto.PermissionEntry;
import com.ulticode.auth.api.service.AuthorizationSnapshotService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.metrics.AdminUseCaseMetrics;
import com.ulticode.modules.admin.port.AdminSubmissionUserDetailStatsReadPort;
import com.ulticode.modules.admin.port.adapter.CancellableQueryExecutor;
import com.ulticode.modules.admin.projection.AdminUserEnricher;
import com.ulticode.submission.api.dto.SubmissionUserDetailStatsSnapshotDTO;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Default Admin-internal implementation of {@link AdminUserDetailQuery}.
 *
 * <p>Round one resolves the Auth account authoritatively. Round two reads the
 * optional App profile, App solution count, Submission snapshot, and Auth
 * authorization snapshot in parallel on one bounded executor. A section is
 * never represented by an empty value when its provider did not answer.
 */
@Slf4j
@Service
public class DefaultAdminUserDetailQuery implements AdminUserDetailQuery {
    private static final String AUTHORIZATION_SNAPSHOT_SOURCE =
            "auth.authorization-snapshot";
    private static final String AUTH_ACCOUNT_TIMEOUT_REASON =
            "Auth account query timed out";
    private static final String AUTH_ACCOUNT_INTERRUPTED_REASON =
            "Auth account query interrupted";
    private static final String DETAIL_TIMEOUT_REASON =
            "Admin user detail query timed out";
    private static final String DETAIL_INTERRUPTED_REASON =
            "Admin user detail query interrupted";
    private static final String DETAIL_REJECTED_REASON =
            "Admin user detail query capacity exceeded";
    private static final String PROFILE_FAILURE_REASON =
            "App profile query unavailable";
    private static final String SOLUTION_FAILURE_REASON =
            "App solution count query unavailable";
    private static final String SUBMISSION_FAILURE_REASON =
            "Submission stats query unavailable";
    private static final int DETAIL_QUERY_POOL_SIZE = 4;
    private static final long DETAIL_WALL_BUDGET_NANOS =
            TimeUnit.MILLISECONDS.toNanos(RpcPolicy.QUERY_TOTAL_BUDGET_MS);
    private static final Map<AdminUseCaseMetrics.Owner, Integer> DETAIL_CALLS = Map.of(
            AdminUseCaseMetrics.Owner.AUTH, 2,
            AdminUseCaseMetrics.Owner.APP, 2,
            AdminUseCaseMetrics.Owner.SUBMISSION, 1);

    private final AdminUserEnricher userEnricher;
    private final AdminSubmissionUserDetailStatsReadPort submissionStatsReadPort;
    private final SolutionReadPort solutionReadPort;
    private final Clock clock;
    private final CancellableQueryExecutor queryExecutor;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES,
            check = false)
    private AuthorizationSnapshotService authorizationSnapshotService;

    @Autowired(required = false)
    private AdminUseCaseMetrics useCaseMetrics;

    /** Production constructor; all required owner read seams are explicit. */
    @Autowired
    public DefaultAdminUserDetailQuery(
            AdminUserEnricher userEnricher,
            AdminSubmissionUserDetailStatsReadPort submissionStatsReadPort,
            SolutionReadPort solutionReadPort,
            Clock clock) {
        this(userEnricher, submissionStatsReadPort, solutionReadPort, null, clock,
                new CancellableQueryExecutor("admin-user-detail-query", DETAIL_QUERY_POOL_SIZE));
    }

    /** Test constructor that keeps the optional Auth snapshot provider explicit. */
    public DefaultAdminUserDetailQuery(
            AdminUserEnricher userEnricher,
            AdminSubmissionUserDetailStatsReadPort submissionStatsReadPort,
            SolutionReadPort solutionReadPort,
            AuthorizationSnapshotService authorizationSnapshotService) {
        this(userEnricher, submissionStatsReadPort, solutionReadPort,
                authorizationSnapshotService, Clock.systemDefaultZone(),
                new CancellableQueryExecutor("admin-user-detail-query-test", DETAIL_QUERY_POOL_SIZE));
    }

    /** Test constructor with an explicit clock for expiry-boundary assertions. */
    public DefaultAdminUserDetailQuery(
            AdminUserEnricher userEnricher,
            AdminSubmissionUserDetailStatsReadPort submissionStatsReadPort,
            SolutionReadPort solutionReadPort,
            AuthorizationSnapshotService authorizationSnapshotService,
            Clock clock) {
        this(userEnricher, submissionStatsReadPort, solutionReadPort,
                authorizationSnapshotService, clock,
                new CancellableQueryExecutor("admin-user-detail-query-test", DETAIL_QUERY_POOL_SIZE));
    }

    DefaultAdminUserDetailQuery(
            AdminUserEnricher userEnricher,
            AdminSubmissionUserDetailStatsReadPort submissionStatsReadPort,
            SolutionReadPort solutionReadPort,
            AuthorizationSnapshotService authorizationSnapshotService,
            Clock clock,
            CancellableQueryExecutor queryExecutor) {
        this.userEnricher = Objects.requireNonNull(userEnricher, "userEnricher");
        this.submissionStatsReadPort = submissionStatsReadPort;
        this.solutionReadPort = solutionReadPort;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.queryExecutor = Objects.requireNonNull(queryExecutor, "queryExecutor");
        this.authorizationSnapshotService = authorizationSnapshotService;
    }

    @PreDestroy
    void shutdownQueryExecutor() {
        queryExecutor.close();
    }

    @Override
    public AdminUserDetailResult loadUserDetail(String userId) {
        AdminUseCaseMetrics metrics = useCaseMetrics;
        if (metrics == null) {
            return loadUserDetailInternal(userId);
        }
        return metrics.observe(
                "I-USER-DETAIL",
                DETAIL_CALLS,
                2,
                AdminUseCaseMetrics.Freshness.REQ,
                result -> result == null || result.failure() != null
                        ? DegradationStatus.UNAVAILABLE
                        : toDegradationStatus(result.availability()),
                () -> loadUserDetailInternal(userId));
    }

    private AdminUserDetailResult loadUserDetailInternal(String userId) {
        if (userId == null || userId.isBlank()) {
            return AdminUserDetailResult.notFound();
        }

        long deadline = System.nanoTime() + DETAIL_WALL_BUDGET_NANOS;
        CancellableQueryExecutor.Query<AuthAccountDTO> accountQuery =
                queryExecutor.submit(() -> userEnricher.findAccountAuthoritatively(userId));
        AuthAccountDTO account;
        try {
            account = queryExecutor.await(
                    accountQuery,
                    remainingNanos(deadline),
                    TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return AdminUserDetailResult.unavailable(AUTH_ACCOUNT_INTERRUPTED_REASON);
        } catch (TimeoutException exception) {
            return AdminUserDetailResult.unavailable(AUTH_ACCOUNT_TIMEOUT_REASON);
        } catch (ExecutionException exception) {
            AdminReadContract.propagate(exception.getCause());
            return accountFailure(exception.getCause());
        }

        if (account == null) {
            return AdminUserDetailResult.notFound();
        }
        if (account.accountId() == null || !userId.equals(account.accountId())) {
            return AdminUserDetailResult.unavailable("Auth account payload invalid");
        }

        AdminUserVO user = toUser(account, null);
        if (remainingNanos(deadline) <= 0) {
            return foundWithUnavailableSections(user, DETAIL_TIMEOUT_REASON);
        }

        CancellableQueryExecutor.Query<OwnerRead<PermissionData>> permissionQuery =
                queryExecutor.submit(() -> readPermissions(userId));
        CancellableQueryExecutor.Query<OwnerRead<UserProfileDTO>> profileQuery =
                queryExecutor.submit(() -> readProfile(userId));
        CancellableQueryExecutor.Query<OwnerRead<Long>> solutionQuery =
                queryExecutor.submit(() -> readSolutionCount(userId));
        CancellableQueryExecutor.Query<OwnerRead<SubmissionUserDetailStatsSnapshotDTO>> submissionQuery =
                queryExecutor.submit(() -> readSubmissionStats(userId));

        List<OwnerRead<Object>> reads = AdminReadContract.<Object>awaitAndClassify(
                queryExecutor,
                "Admin user detail",
                Math.max(0L, remainingNanos(deadline)),
                TimeUnit.NANOSECONDS,
                permissionQuery,
                profileQuery,
                solutionQuery,
                submissionQuery);
        String fallbackReason = detailFallbackReason(reads);
        OwnerRead<PermissionData> permissions = normalizeUnavailable(
                typedRead(reads.get(0)), fallbackReason,
                "Authorization snapshot query failed");
        OwnerRead<UserProfileDTO> profile = normalizeUnavailable(
                typedRead(reads.get(1)), fallbackReason, PROFILE_FAILURE_REASON);
        OwnerRead<Long> solution = normalizeUnavailable(
                typedRead(reads.get(2)), fallbackReason, SOLUTION_FAILURE_REASON);
        OwnerRead<SubmissionUserDetailStatsSnapshotDTO> submission = normalizeUnavailable(
                typedRead(reads.get(3)), fallbackReason, SUBMISSION_FAILURE_REASON);
        return assemble(user, profile, submission, solution, permissions);
    }

    private AdminUserDetailResult accountFailure(Throwable cause) {
        if (cause instanceof Error error) {
            throw error;
        }
        if (cause instanceof BusinessException exception
                && exception.getErrorCode() == AdminErrorCode.USER_NOT_FOUND) {
            return AdminUserDetailResult.notFound();
        }
        if (cause instanceof BusinessException exception
                && exception.getErrorCode() != AdminErrorCode.OWNER_QUERY_UNAVAILABLE) {
            throw exception;
        }
        log.warn("Auth account query failed: {}",
                cause == null ? "unknown" : cause.getClass().getSimpleName());
        return AdminUserDetailResult.unavailable("Auth account query unavailable");
    }

    private static long remainingNanos(long deadline) {
        return Math.max(0L, deadline - System.nanoTime());
    }

    private AdminUserDetailResult foundWithUnavailableSections(
            AdminUserVO user, String reason) {
        AdminUserDetailResult.Section unavailable =
                AdminUserDetailResult.Section.unavailable(reason);
        AdminUserDetailResult result = AdminUserDetailResult.found(
                user, unavailable, unavailable, unavailable, null);
        applyWireStatus(user, result);
        return result;
    }

    private OwnerRead<UserProfileDTO> readProfile(String userId) {
        try {
            AdminUserEnricher.ProfileDetail detail =
                    userEnricher.findProfileWithStatus(userId);
            if (detail == null || detail.status() != DegradationStatus.OK) {
                return OwnerRead.unavailable(PROFILE_FAILURE_REASON);
            }
            return OwnerRead.available(detail.profile());
        } catch (RuntimeException exception) {
            AdminReadContract.propagate(exception);
            log.warn("App profile query failed for {}: {}", userId,
                    exception.getClass().getSimpleName());
            return OwnerRead.unavailable(PROFILE_FAILURE_REASON);
        }
    }

    private OwnerRead<Long> readSolutionCount(String userId) {
        if (solutionReadPort == null) {
            return OwnerRead.unavailable(SOLUTION_FAILURE_REASON);
        }
        try {
            long count = solutionReadPort.countByUserId(userId);
            if (count < 0) {
                return OwnerRead.unavailable("App solution count payload invalid");
            }
            return OwnerRead.available(count);
        } catch (RuntimeException exception) {
            AdminReadContract.propagate(exception);
            log.warn("App solution count query failed for {}: {}", userId,
                    exception.getClass().getSimpleName());
            return OwnerRead.unavailable(SOLUTION_FAILURE_REASON);
        }
    }

    private OwnerRead<SubmissionUserDetailStatsSnapshotDTO> readSubmissionStats(String userId) {
        if (submissionStatsReadPort == null) {
            return OwnerRead.unavailable(SUBMISSION_FAILURE_REASON);
        }
        try {
            SubmissionUserDetailStatsSnapshotDTO snapshot =
                    submissionStatsReadPort.loadUserDetailStats(userId);
            if (snapshot == null) {
                return OwnerRead.unavailable(SUBMISSION_FAILURE_REASON);
            }
            return OwnerRead.available(snapshot);
        } catch (RuntimeException exception) {
            AdminReadContract.propagate(exception);
            log.warn("Submission stats query failed for {}: {}", userId,
                    exception.getClass().getSimpleName());
            return OwnerRead.unavailable(SUBMISSION_FAILURE_REASON);
        }
    }

    private OwnerRead<PermissionData> readPermissions(String userId) {
        if (authorizationSnapshotService == null) {
            return OwnerRead.unavailable(
                    "Authorization snapshot provider unavailable");
        }

        RpcResult<AuthorizationSnapshotDTO> rpc;
        try {
            rpc = authorizationSnapshotService.getSnapshot(userId);
        } catch (RuntimeException exception) {
            AdminReadContract.propagate(exception);
            log.warn("Authorization snapshot query failed for {}: {}", userId,
                    exception.getClass().getSimpleName());
            return OwnerRead.unavailable("Authorization snapshot query failed");
        }
        if (rpc == null) {
            return OwnerRead.unavailable("Authorization snapshot returned null");
        }
        OwnerRead<AuthorizationSnapshotDTO> read =
                AdminReadContract.classify("Auth", rpc);
        if (!read.available()) {
            return OwnerRead.unavailable("Authorization snapshot returned failure");
        }
        if (read.value() == null) {
            return OwnerRead.unavailable("Authorization snapshot returned null");
        }

        AuthorizationSnapshotDTO snapshot = read.value();
        if (snapshot.accountId() == null
                || !userId.equals(snapshot.accountId())
                || snapshot.role() == null
                || snapshot.role().isBlank()
                || snapshot.permissions() == null
                || invalidPermissionEntries(snapshot.permissionEntries())
                || invalidFlatPermissions(snapshot)) {
            return OwnerRead.unavailable(
                    "Authorization snapshot payload invalid");
        }

        List<AdminUserVO.PermissionInfo> permissions = toPermissionInfos(snapshot);
        AdminUserDetailResult.PermissionSnapshot completeSnapshot =
                new AdminUserDetailResult.PermissionSnapshot(
                        AUTHORIZATION_SNAPSHOT_SOURCE,
                        snapshot.role(),
                        snapshot.permissions(),
                        snapshot.version());
        return OwnerRead.available(new PermissionData(permissions, completeSnapshot));
    }

    private AdminUserDetailResult assemble(
            AdminUserVO user,
            OwnerRead<UserProfileDTO> profile,
            OwnerRead<SubmissionUserDetailStatsSnapshotDTO> submission,
            OwnerRead<Long> solution,
            OwnerRead<PermissionData> permissions) {
        OwnerRead<UserProfileDTO> safeProfile = profile == null
                ? OwnerRead.unavailable(PROFILE_FAILURE_REASON) : profile;
        OwnerRead<SubmissionUserDetailStatsSnapshotDTO> safeSubmission = submission == null
                ? OwnerRead.unavailable(SUBMISSION_FAILURE_REASON) : submission;
        OwnerRead<Long> safeSolution = solution == null
                ? OwnerRead.unavailable(SOLUTION_FAILURE_REASON) : solution;
        OwnerRead<PermissionData> safePermissions = permissions == null
                ? OwnerRead.unavailable("Authorization snapshot query failed") : permissions;

        if (safeProfile.available()) {
            user.setName(safeProfile.value() == null ? null : safeProfile.value().name());
            user.setAvatar(safeProfile.value() == null ? null : safeProfile.value().avatar());
        }
        if (safePermissions.available() && safePermissions.value() != null) {
            user.setPermissions(safePermissions.value().permissions());
        }

        AdminUserDetailResult.Section profileSection = section(safeProfile);
        AdminUserDetailResult.Section statsSection;
        boolean submissionAvailable = safeSubmission.available() && safeSubmission.value() != null;
        boolean solutionAvailable = safeSolution.available() && safeSolution.value() != null;
        if (submissionAvailable && solutionAvailable) {
            SubmissionUserDetailStatsSnapshotDTO submissionStats = safeSubmission.value();
            try {
                AdminUserVO.UserStatsInfo stats = new AdminUserVO.UserStatsInfo();
                stats.setTotalSubmissions(toInt(submissionStats.submissionCount()));
                stats.setAcceptedSubmissions(toInt(submissionStats.acceptedProblemCount()));
                stats.setTotalSolutions(toInt(safeSolution.value()));
                stats.setStreak(submissionStats.streak());
                user.setStats(stats);
                statsSection = AdminUserDetailResult.Section.ok();
            } catch (ArithmeticException exception) {
                statsSection = new AdminUserDetailResult.Section(
                        AdminUserDetailResult.Availability.UNAVAILABLE,
                        "User statistics payload invalid");
            }
        } else if (submissionAvailable || solutionAvailable) {
            String reason = submissionAvailable
                    ? reason(safeSolution, SOLUTION_FAILURE_REASON)
                    : reason(safeSubmission, SUBMISSION_FAILURE_REASON);
            statsSection = new AdminUserDetailResult.Section(
                    AdminUserDetailResult.Availability.PARTIAL, reason);
        } else {
            statsSection = new AdminUserDetailResult.Section(
                    AdminUserDetailResult.Availability.UNAVAILABLE,
                    reason(safeSubmission, SUBMISSION_FAILURE_REASON) + "; "
                            + reason(safeSolution, SOLUTION_FAILURE_REASON));
        }

        boolean permissionsAvailable = safePermissions.available()
                && safePermissions.value() != null;
        AdminUserDetailResult result = AdminUserDetailResult.found(
                user,
                profileSection,
                statsSection,
                permissionsAvailable
                        ? AdminUserDetailResult.Section.ok()
                        : section(safePermissions),
                permissionsAvailable ? safePermissions.value().snapshot() : null);
        applyWireStatus(user, result);
        return result;
    }

    private static AdminUserDetailResult.Section section(OwnerRead<?> read) {
        return read.available()
                ? AdminUserDetailResult.Section.ok()
                : AdminUserDetailResult.Section.unavailable(read.reason());
    }

    private static String reason(OwnerRead<?> read, String fallback) {
        return read.reason() == null ? fallback : read.reason();
    }

    private static int toInt(long value) {
        return Math.toIntExact(value);
    }

    private static void applyWireStatus(AdminUserVO user, AdminUserDetailResult result) {
        user.setDegradationStatus(result.availability() == AdminUserDetailResult.Availability.OK
                ? null : toDegradationStatus(result.availability()));
        user.setDetailStatus(toDegradationStatus(result.availability()));
        user.setProfileStatus(toDegradationStatus(result.profile().status()));
        user.setProfileReason(result.profile().reason());
        user.setStatsStatus(toDegradationStatus(result.stats().status()));
        user.setStatsReason(result.stats().reason());
        user.setPermissionsStatus(toDegradationStatus(result.permissions().status()));
        user.setPermissionsReason(result.permissions().reason());
    }

    private static DegradationStatus toDegradationStatus(
            AdminUserDetailResult.Availability availability) {
        return switch (availability) {
            case OK -> DegradationStatus.OK;
            case PARTIAL -> DegradationStatus.PARTIAL;
            case UNAVAILABLE -> DegradationStatus.UNAVAILABLE;
        };
    }

    private boolean invalidFlatPermissions(AuthorizationSnapshotDTO snapshot) {
        List<PermissionEntry> entries = snapshot.permissionEntries();
        if (entries != null && !entries.isEmpty()) {
            return false;
        }
        for (String value : snapshot.permissions()) {
            if (value == null || flatPermissionInfo(value) == null) {
                return true;
            }
        }
        return false;
    }

    private boolean invalidPermissionEntries(List<PermissionEntry> entries) {
        if (entries == null) {
            return false;
        }
        for (PermissionEntry entry : entries) {
            if (entry == null
                    || entry.action() == null
                    || entry.action().isBlank()
                    || entry.resource() == null
                    || entry.resource().isBlank()
                    || entry.source() == null
                    || entry.source().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private List<AdminUserVO.PermissionInfo> toPermissionInfos(
            AuthorizationSnapshotDTO snapshot) {
        List<PermissionEntry> entries = snapshot.permissionEntries();
        if (entries == null || entries.isEmpty()) {
            return snapshot.permissions().stream()
                    .map(this::flatPermissionInfo)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(
                            AdminUserVO.PermissionInfo::getAction)
                            .thenComparing(AdminUserVO.PermissionInfo::getResource))
                    .toList();
        }

        List<AdminUserVO.PermissionInfo> infos = new ArrayList<>();
        for (PermissionEntry entry : entries) {
            OffsetDateTime expiresAt = entry.expiresAt();
            if (expiresAt != null && !expiresAt.toInstant().isAfter(clock.instant())) {
                continue;
            }
            AdminUserVO.PermissionInfo info = new AdminUserVO.PermissionInfo();
            info.setAction(entry.action());
            info.setResource(entry.resource());
            info.setSource(entry.source());
            info.setExpiresAt(expiresAt == null ? null : expiresAt.toLocalDateTime());
            infos.add(info);
        }
        return List.copyOf(infos);
    }

    private AdminUserVO.PermissionInfo flatPermissionInfo(String value) {
        if (value == null) {
            return null;
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            return null;
        }
        AdminUserVO.PermissionInfo info = new AdminUserVO.PermissionInfo();
        info.setAction(value.substring(0, separator));
        info.setResource(value.substring(separator + 1));
        info.setSource("snapshot");
        return info;
    }

    private static String detailFallbackReason(List<OwnerRead<Object>> reads) {
        for (OwnerRead<Object> read : reads) {
            String reason = read.reason();
            if (reason == null) {
                continue;
            }
            if (reason.contains("interrupted")) {
                return DETAIL_INTERRUPTED_REASON;
            }
            if (reason.contains("timed out")) {
                return DETAIL_TIMEOUT_REASON;
            }
            if (reason.contains("capacity exceeded")) {
                return DETAIL_REJECTED_REASON;
            }
        }
        return null;
    }

    private static <T> OwnerRead<T> normalizeUnavailable(
            OwnerRead<T> read, String fallbackReason, String defaultReason) {
        if (read == null) {
            return OwnerRead.unavailable(fallbackReason == null ? defaultReason : fallbackReason);
        }
        if (read.available()) {
            return read;
        }
        String reason = read.reason();
        if (reason == null || reason.startsWith("Admin user detail owner query ")) {
            return OwnerRead.unavailable(fallbackReason == null ? defaultReason : fallbackReason);
        }
        return read;
    }

    @SuppressWarnings("unchecked")
    private static <T> OwnerRead<T> typedRead(OwnerRead<?> read) {
        return (OwnerRead<T>) read;
    }

    private static AdminUserVO toUser(AuthAccountDTO account, UserProfileDTO profile) {
        AdminUserVO user = new AdminUserVO();
        user.setId(account.accountId());
        user.setUsername(account.username());
        user.setEmail(account.email());
        user.setRole(account.role());
        user.setIsActive(account.active());
        user.setIsBanned(account.banned());
        user.setBanReason(account.bannedReason());
        user.setBannedUntil(account.bannedUntil());
        user.setJoinedAt(account.joinedAt());
        user.setLastLoginAt(account.lastLoginAt());
        if (profile != null) {
            user.setName(profile.name());
            user.setAvatar(profile.avatar());
        }
        return user;
    }

    private record PermissionData(
            List<AdminUserVO.PermissionInfo> permissions,
            AdminUserDetailResult.PermissionSnapshot snapshot) {
    }
}
