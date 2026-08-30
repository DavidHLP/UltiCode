package com.ulticode.modules.reconciliation;

import com.ulticode.auth.api.dto.AuthReconciliationOrphanCounts;
import com.ulticode.auth.api.service.ReconciliationQueryService;
import com.ulticode.app.api.dto.ReconciliationOrphanCounts;
import com.ulticode.app.api.service.AppReconciliationReadPort;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.submission.api.dto.SubmissionUserReferenceCountDTO;
import com.ulticode.submission.api.service.SubmissionReconciliationReadPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Nightly reconciliation job and orphan scanner (P5-RECONCILE-001),
 * rebuilt on owner RPC/read-port aggregation
 * (ADR-P7-OWNER-BOUNDARY-RECONCILIATION-20260802 Decision 4).
 *
 * <p>No cross-owner JdbcTemplate SQL remains. Facts are gathered per
 * owner:
 * <ul>
 *   <li>Auth: {@link ReconciliationQueryService} (Dubbo) — non-deleted
 *       user count, physical-existence id check, 4 Auth-internal orphan
 *       counts;</li>
 *   <li>App: {@link AppReconciliationReadPort} (local port) —
 *       user_profiles count and 8 App child orphan counts;</li>
 *   <li>Submission: {@link SubmissionReconciliationReadPort} (Dubbo) —
 *       bounded full/incremental submission user-reference facts;</li>
 *   <li>Admin: local {@code audit_logs.performer_id} orphan check and
 *       the {@code reconciliation_runs} persistence. A MySQL advisory lock
 *       prevents duplicate multi-replica runs.</li>
 * </ul>
 *
 * <p>Orphan semantics preserved: a child row is an orphan only if the
 * parent id does not exist at all (soft-deleted parents are NOT
 * orphans). Nightly cron (0 0 2 * * *) preserved.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerReconciler {

    private static final String RECONCILIATION_LOCK = "ulticode:admin:reconciliation";
    private static final int RECONCILIATION_PAGE_SIZE = SubmissionReconciliationReadPort.MAX_PAGE_SIZE;

    private final ReconciliationRunMapper runMapper;
    private final UuidGenerator uuidGenerator;
    private final AppReconciliationReadPort appReconciliationReadPort;
    private final SubmissionReconciliationReadPort submissionReconciliationReadPort;
    private final AuditOrphanMapper auditOrphanMapper;
    private final MeterRegistry meterRegistry;

    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ReconciliationQueryService authQueryService;

    /** Reconciliation pair for vertical-split table count divergence checks. */
    record ReconciliationPair(String sourceTable, String targetTable, String owner) {}

    // The users → user_profiles dual-write pair is removed: profile columns
    // have been dropped from users (P5-USERPROFILE-001 contract phase).
    // Reconciliation infrastructure remains for future pairs and orphan detection.
    private static final List<ReconciliationPair> RECONCILIATION_PAIRS = List.of();


    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void scheduledReconciliation() {
        runReconciliation();
    }

    /** Execute a full reconciliation run. */
    @Transactional
    public ReconciliationRun runReconciliation() {
        return runReconciliationInternal(null);
    }

    /** Execute a bounded incremental reconciliation from an inclusive watermark. */
    @Transactional
    public ReconciliationRun runIncrementalReconciliation(LocalDateTime createdSince) {
        if (createdSince == null) {
            throw new IllegalArgumentException("createdSince is required for incremental reconciliation");
        }
        return runReconciliationInternal(createdSince);
    }

    private ReconciliationRun runReconciliationInternal(LocalDateTime createdSince) {
        String mode = createdSince == null ? "FULL" : "INCREMENTAL";
        Integer leaseResult;
        try {
            leaseResult = runMapper.tryAcquireLease(RECONCILIATION_LOCK);
        } catch (RuntimeException exception) {
            return persistFailure(mode, "GET_LOCK failed: " + failureReason(exception));
        }
        if (leaseResult == null) {
            return persistFailure(mode, "GET_LOCK returned NULL");
        }
        if (leaseResult != 0 && leaseResult != 1) {
            return persistFailure(mode, "GET_LOCK returned unexpected result: " + leaseResult);
        }
        if (leaseResult == 0) {
            incrementCounter("reconciliation.skipped", "reason", "lease_busy");
            log.info("Reconciliation skipped: another replica owns {}", RECONCILIATION_LOCK);
            return skippedRun(mode);
        }

        try {
            String runId = uuidGenerator.newId();
            LocalDateTime startedAt = LocalDateTime.now();
            ReconciliationRun run = new ReconciliationRun();
            run.setRunId(runId);
            run.setStartedAt(startedAt);
            run.setOwner("ALL");
            run.setStatus("RUNNING");
            run.setDivergenceCount(0);
            run.setOrphanCount(0);
            runMapper.insert(run);

            List<ReconciliationResult> reconResults = new ArrayList<>();
            List<OrphanDetectionResult> orphanResults = new ArrayList<>();
            int totalDivergence = 0;
            int totalOrphans = 0;
            String failureReason = null;

            try {
                for (ReconciliationPair pair : RECONCILIATION_PAIRS) {
                    ReconciliationResult result = reconcilePair(pair);
                    reconResults.add(result);
                    if (!result.isDriftFree()) {
                        totalDivergence++;
                    }
                }

                orphanResults.addAll(authOrphans());
                orphanResults.add(submissionOrphans(createdSince));
                orphanResults.addAll(appOrphans());
                orphanResults.add(auditLogsOrphans());
                for (OrphanDetectionResult result : orphanResults) {
                    if (!result.isOrphanFree()) {
                        totalOrphans++;
                    }
                }

                run.setDivergenceCount(totalDivergence);
                run.setOrphanCount(totalOrphans);
                run.setStatus("COMPLETED");
            } catch (Exception exception) {
                failureReason = failureReason(exception);
                log.error("Reconciliation run {} failed: {}", runId, failureReason, exception);
                run.setStatus("FAILED");
                incrementCounter("reconciliation.failures", "mode", mode);
            }

            run.setFinishedAt(LocalDateTime.now());
            run.setDetail(buildDetailJson(mode, reconResults, orphanResults, failureReason));
            runMapper.updateById(run);
            incrementCounter("reconciliation.runs", "mode", mode, "status", run.getStatus());
            logReconciliationResults(reconResults, orphanResults, totalDivergence, totalOrphans);
            return run;
        } finally {
            try {
                runMapper.releaseLease(RECONCILIATION_LOCK);
            } catch (RuntimeException exception) {
                log.error("Unable to release reconciliation lease {}", RECONCILIATION_LOCK, exception);
            }
        }
    }

    private ReconciliationRun persistFailure(String mode, String reason) {
        String runId = uuidGenerator.newId();
        ReconciliationRun run = new ReconciliationRun();
        run.setRunId(runId);
        run.setStartedAt(LocalDateTime.now());
        run.setOwner("ALL");
        run.setStatus("RUNNING");
        run.setDivergenceCount(0);
        run.setOrphanCount(0);
        runMapper.insert(run);
        run.setFinishedAt(LocalDateTime.now());
        run.setStatus("FAILED");
        run.setDetail(buildDetailJson(mode, List.of(), List.of(), reason));
        runMapper.updateById(run);
        incrementCounter("reconciliation.failures", "mode", mode);
        incrementCounter("reconciliation.runs", "mode", mode, "status", "FAILED");
        log.error("Reconciliation run {} failed before lease acquisition: {}", runId, reason);
        return run;
    }

    private ReconciliationRun skippedRun(String mode) {
        ReconciliationRun run = new ReconciliationRun();
        run.setOwner("ALL");
        run.setStatus("SKIPPED");
        run.setDivergenceCount(0);
        run.setOrphanCount(0);
        run.setStartedAt(LocalDateTime.now());
        run.setFinishedAt(LocalDateTime.now());
        run.setDetail("{\"mode\":\"" + mode
                + "\",\"status\":\"SKIPPED\",\"reason\":\"lease_busy\"}");
        return run;
    }

    private void incrementCounter(String name, String... tags) {
        if (meterRegistry == null) {
            return;
        }
        Counter counter = meterRegistry.counter(name, tags);
        if (counter != null) {
            counter.increment();
        }
    }

    private static String failureReason(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }

    /**
     * Reconcile a vertical-split pair: the source owner counts rows
     * via RPC; the target owner counts via the local read port.
     */
    ReconciliationResult reconcilePair(ReconciliationPair pair) {
        long sourceCount = 0;
        long targetCount = 0;

        if ("users".equals(pair.sourceTable()) && "user_profiles".equals(pair.targetTable())) {
            RpcResult<Long> authCount = authQueryService.countActiveUsers();
            if (authCount != null && authCount.success() && authCount.data() != null) {
                sourceCount = authCount.data();
            }
            targetCount = appReconciliationReadPort.countUserProfiles();
        }

        return new ReconciliationResult(
                pair.sourceTable() + " → " + pair.targetTable(),
                pair.owner(),
                sourceCount,
                targetCount);
    }

    /** Four Auth-internal orphan checks via the auth Dubbo provider. */
    private List<OrphanDetectionResult> authOrphans() {
        if (authQueryService == null) {
            throw authUnavailable();
        }
        RpcResult<AuthReconciliationOrphanCounts> result = authQueryService.countAuthOrphans();
        if (result == null || !result.success() || result.data() == null) {
            throw authUnavailable();
        }
        AuthReconciliationOrphanCounts counts = result.data();
        return List.of(
                orphan("refresh_tokens", "user_id", "Auth", "users", "Auth", counts.refreshTokens()),
                orphan("password_resets", "user_id", "Auth", "users", "Auth", counts.passwordResets()),
                orphan("oauth_provider_identities", "user_id", "Auth", "users", "Auth", counts.oauthProviderIdentities()),
                orphan("user_permissions", "user_id", "Auth", "users", "Auth", counts.userPermissions()));
    }

    /** Bounded Submission-owned orphan scan for full or incremental runs. */
    private OrphanDetectionResult submissionOrphans(LocalDateTime createdSince) {
        if (submissionReconciliationReadPort == null) {
            throw submissionUnavailable();
        }
        String afterAccountId = "";
        long missing = 0L;
        while (true) {
            List<SubmissionUserReferenceCountDTO> references =
                    submissionReconciliationReadPort.findUserReferenceCounts(
                            afterAccountId, createdSince, RECONCILIATION_PAGE_SIZE);
            if (references == null) {
                throw submissionUnavailable();
            }
            if (references.isEmpty()) {
                break;
            }
            if (references.size() > RECONCILIATION_PAGE_SIZE) {
                throw submissionUnavailable();
            }
            Set<String> candidates = new HashSet<>();
            String previousAccountId = afterAccountId;
            for (SubmissionUserReferenceCountDTO reference : references) {
                if (reference == null || reference.accountId() == null
                        || reference.accountId().isBlank() || reference.rowCount() < 0
                        || !candidates.add(reference.accountId())
                        || reference.accountId().compareTo(previousAccountId) <= 0) {
                    throw submissionUnavailable();
                }
                previousAccountId = reference.accountId();
            }
            Set<String> existing = existingUserIds(candidates);
            for (SubmissionUserReferenceCountDTO reference : references) {
                if (!existing.contains(reference.accountId())) {
                    missing += reference.rowCount();
                }
            }
            String nextAccountId = references.get(references.size() - 1).accountId();
            if (nextAccountId.compareTo(afterAccountId) <= 0) {
                throw submissionUnavailable();
            }
            afterAccountId = nextAccountId;
            if (references.size() < RECONCILIATION_PAGE_SIZE) {
                break;
            }
        }
        return orphan("submissions", "user_id", "Submission", "users", "Auth", missing);
    }

    /** Eight App child orphan checks; Submission rows are owner facts above. */
    private List<OrphanDetectionResult> appOrphans() {
        ReconciliationOrphanCounts counts = appReconciliationReadPort.countOrphans();
        return List.of(
                orphan("solutions", "user_id", "App", "users", "Auth", counts.solutions()),
                orphan("forum_posts", "user_id", "App", "users", "Auth", counts.forumPosts()),
                orphan("notifications", "user_id", "App", "users", "Auth", counts.notifications()),
                orphan("user_profiles", "account_id", "App", "users", "Auth", counts.userProfiles()),
                orphan("contest_participants", "user_id", "App", "users", "Auth", counts.contestParticipants()),
                orphan("user_achievements", "user_id", "App", "users", "Auth", counts.userAchievements()),
                orphan("user_follows", "follower_id", "App", "users", "Auth", counts.userFollowsByFollower()),
                orphan("user_follows", "following_id", "App", "users", "Auth", counts.userFollowsByFollowing()));
    }

    /** Admin-local audit_logs candidates checked against Auth physical existence in bounded pages. */
    private OrphanDetectionResult auditLogsOrphans() {
        long missing = 0;
        int offset = 0;
        final int pageSize = 500;
        while (true) {
            List<AuditReferenceCount> references = auditOrphanMapper.auditPerformerIds(offset, pageSize);
            if (references == null || references.isEmpty()) {
                break;
            }
            Set<String> candidates = references.stream()
                    .map(AuditReferenceCount::getPerformerId)
                    .collect(Collectors.toSet());
            Set<String> existing = existingUserIds(candidates);
            missing += references.stream()
                    .filter(reference -> !existing.contains(reference.getPerformerId()))
                    .mapToLong(AuditReferenceCount::getRowCount)
                    .sum();
            if (references.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }
        return orphan("audit_logs", "performer_id", "Admin", "users", "Auth", missing);
    }

    private Set<String> existingUserIds(Set<String> candidates) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        if (authQueryService == null) {
            throw authUnavailable();
        }
        List<String> ids = new ArrayList<>(candidates);
        Set<String> existing = new HashSet<>();
        for (int start = 0; start < ids.size(); start += 500) {
            Set<String> batch = Set.copyOf(ids.subList(start, Math.min(start + 500, ids.size())));
            RpcResult<Set<String>> result = authQueryService.existingUserIds(batch);
            if (result == null || !result.success() || result.data() == null) {
                throw authUnavailable();
            }
            existing.addAll(result.data());
        }
        return existing;
    }

    private BusinessException authUnavailable() {
        return new BusinessException(BaseErrorCode.UNKNOWN_ERROR, "Auth reconciliation owner unavailable");
    }
    private BusinessException submissionUnavailable() {
        return new BusinessException(
                BaseErrorCode.UNKNOWN_ERROR, "Submission reconciliation owner unavailable");
    }
    private static OrphanDetectionResult orphan(String childTable, String childColumn,
                                                String childOwner, String parentTable,
                                                String parentOwner, long count) {
        return new OrphanDetectionResult(childTable, childOwner, childColumn,
                parentTable, parentOwner, count);
    }

    private String buildDetailJson(String mode,
                                   List<ReconciliationResult> reconResults,
                                   List<OrphanDetectionResult> orphanResults,
                                   String failureReason) {
        StringBuilder sb = new StringBuilder("{\"mode\":\"")
                .append(jsonEscape(mode)).append("\",\"reconciliation\":[");
        for (int i = 0; i < reconResults.size(); i++) {
            if (i > 0) sb.append(",");
            ReconciliationResult r = reconResults.get(i);
            sb.append(String.format("{\"table\":\"%s\",\"source\":%d,\"target\":%d,\"drift\":%s}",
                    jsonEscape(r.getTableName()), r.getSourceCount(), r.getTargetCount(),
                    r.isDriftFree() ? "false" : "true"));
        }
        sb.append("],\"orphans\":[");
        for (int i = 0; i < orphanResults.size(); i++) {
            if (i > 0) sb.append(",");
            OrphanDetectionResult o = orphanResults.get(i);
            sb.append(String.format("{\"child\":\"%s\",\"parent\":\"%s\",\"orphans\":%d}",
                    jsonEscape(o.getChildTable()), jsonEscape(o.getParentTable()), o.getOrphanCount()));
        }
        sb.append("]");
        if (failureReason != null) {
            sb.append(",\"error\":\"").append(jsonEscape(failureReason)).append("\"");
        }
        return sb.append("}").toString();
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
    private void logReconciliationResults(List<ReconciliationResult> reconResults,
                                          List<OrphanDetectionResult> orphanResults,
                                          int totalDivergence, int totalOrphans) {
        for (ReconciliationResult r : reconResults) {
            if (r.isDriftFree()) {
                log.info("Reconciliation: {}", r.describe());
            } else {
                log.warn("Reconciliation DRIFT: {}", r.describe());
            }
        }
        for (OrphanDetectionResult o : orphanResults) {
            if (o.isOrphanFree()) {
                log.info("Orphan scan: {}", o.describe());
            } else {
                log.warn("Orphan scan FOUND: {}", o.describe());
            }
        }
        log.info("Reconciliation run complete: {} divergence, {} orphan tables",
                totalDivergence, totalOrphans);
    }
}
