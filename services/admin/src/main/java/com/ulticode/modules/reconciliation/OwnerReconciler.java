package com.ulticode.modules.reconciliation;

import com.ulticode.auth.api.dto.AuthReconciliationOrphanCounts;
import com.ulticode.auth.api.service.ReconciliationQueryService;
import com.ulticode.app.api.dto.ReconciliationOrphanCounts;
import com.ulticode.app.api.service.AppReconciliationReadPort;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.uuid.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
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
 *       user_profiles count and 9 App child orphan counts;</li>
 *   <li>Admin: local {@code audit_logs.performer_id} orphan check and
 *       the {@code reconciliation_runs} persistence.</li>
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

    private final ReconciliationRunMapper runMapper;
    private final UuidGenerator uuidGenerator;
    private final AppReconciliationReadPort appReconciliationReadPort;
    private final AuditOrphanMapper auditOrphanMapper;

    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = 3000, retries = 0, check = false)
    private ReconciliationQueryService authQueryService;

    /** Reconciliation pair for vertical-split table count divergence checks. */
    record ReconciliationPair(String sourceTable, String targetTable, String owner) {}

    // The users → user_profiles dual-write pair is removed: profile columns
    // have been dropped from users (P5-USERPROFILE-001 contract phase).
    // Reconciliation infrastructure remains for future pairs and orphan detection.
    private static final List<ReconciliationPair> RECONCILIATION_PAIRS = List.of();

    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledReconciliation() {
        runReconciliation();
    }

    /**
     * Execute a full reconciliation run: count divergence check + orphan scan.
     *
     * @return the persisted run record
     */
    public ReconciliationRun runReconciliation() {
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

        try {
            for (ReconciliationPair pair : RECONCILIATION_PAIRS) {
                ReconciliationResult result = reconcilePair(pair);
                reconResults.add(result);
                if (!result.isDriftFree()) {
                    totalDivergence++;
                }
            }

            orphanResults.addAll(authOrphans());
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
        } catch (Exception e) {
            log.error("Reconciliation run {} failed: {}", runId, e.getMessage(), e);
            run.setStatus("FAILED");
        }

        run.setFinishedAt(LocalDateTime.now());
        run.setDetail(buildDetailJson(reconResults, orphanResults));
        runMapper.updateById(run);

        logReconciliationResults(reconResults, orphanResults, totalDivergence, totalOrphans);

        return run;
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

    /** Nine App child orphan checks via the app read port. */
    private List<OrphanDetectionResult> appOrphans() {
        ReconciliationOrphanCounts counts = appReconciliationReadPort.countOrphans();
        return List.of(
                orphan("submissions", "user_id", "App", "users", "Auth", counts.submissions()),
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

    private static OrphanDetectionResult orphan(String childTable, String childColumn,
                                                String childOwner, String parentTable,
                                                String parentOwner, long count) {
        return new OrphanDetectionResult(childTable, childOwner, childColumn,
                parentTable, parentOwner, count);
    }

    private String buildDetailJson(List<ReconciliationResult> reconResults,
                                   List<OrphanDetectionResult> orphanResults) {
        StringBuilder sb = new StringBuilder("{\"reconciliation\":[");
        for (int i = 0; i < reconResults.size(); i++) {
            if (i > 0) sb.append(",");
            ReconciliationResult r = reconResults.get(i);
            sb.append(String.format("{\"table\":\"%s\",\"source\":%d,\"target\":%d,\"drift\":%s}",
                    r.getTableName(), r.getSourceCount(), r.getTargetCount(),
                    r.isDriftFree() ? "false" : "true"));
        }
        sb.append("],\"orphans\":[");
        for (int i = 0; i < orphanResults.size(); i++) {
            if (i > 0) sb.append(",");
            OrphanDetectionResult o = orphanResults.get(i);
            sb.append(String.format("{\"child\":\"%s\",\"parent\":\"%s\",\"orphans\":%d}",
                    o.getChildTable(), o.getParentTable(), o.getOrphanCount()));
        }
        sb.append("]}");
        return sb.toString();
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
