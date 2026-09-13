package com.ulticode.modules.reconciliation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.auth.api.dto.AuthReconciliationOrphanCounts;
import com.ulticode.auth.api.service.ReconciliationQueryService;
import com.ulticode.admin.error.AdminReadContract;
import com.ulticode.admin.error.AdminReadContract.OwnerRead;
import com.ulticode.common.lease.FencedLease;
import com.ulticode.common.lifecycle.DrainGate;
import com.ulticode.app.api.dto.ReconciliationOrphanCounts;
import com.ulticode.app.api.service.AppReconciliationReadPort;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.submission.api.dto.SubmissionUserReferenceCountDTO;
import com.ulticode.submission.api.service.SubmissionReconciliationReadPort;
import com.ulticode.notification.api.dto.NotificationUserReferenceCountDTO;
import com.ulticode.notification.api.service.NotificationReconciliationReadPort;
import com.ulticode.modules.lease.FencedJobLeaseService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
 *       user_profiles count and 7 App child orphan counts;</li>
 *   <li>Submission: {@link SubmissionReconciliationReadPort} (Dubbo) —
 *       bounded full/incremental submission user-reference facts;</li>
 *   <li>Notification: {@link NotificationReconciliationReadPort} (Dubbo) —
 *       bounded full/incremental notification user-reference facts;</li>
 *   <li>Admin: local {@code audit_logs.performer_id} orphan check and
 *       the {@code reconciliation_runs} persistence. A database-clock-backed
 *       fenced lease prevents duplicate multi-replica runs.</li>
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

    private static final String RECONCILIATION_LEASE = "admin:reconciliation";
    private static final int RECONCILIATION_PAGE_SIZE = SubmissionReconciliationReadPort.MAX_PAGE_SIZE;
    private static final int NOTIFICATION_RECONCILIATION_PAGE_SIZE =
            NotificationReconciliationReadPort.MAX_PAGE_SIZE;
    private static final int MAX_RECONCILIATION_PAGES = 32;

    private final ReconciliationRunMapper runMapper;
    private final UuidGenerator uuidGenerator;
    private final AppReconciliationReadPort appReconciliationReadPort;
    private final SubmissionReconciliationReadPort submissionReconciliationReadPort;
    private final NotificationReconciliationReadPort notificationReconciliationReadPort;
    private final AuditOrphanMapper auditOrphanMapper;
    private final MeterRegistry meterRegistry;
    private final FencedJobLeaseService fencedJobLeaseService;
    private final ObjectMapper objectMapper;
    private final DrainGate drainGate = new DrainGate();

    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ReconciliationQueryService authQueryService;

    @Scheduled(scheduler = "adminReconciliationScheduler", cron = "0 0 2 * * *")
    @Transactional
    public void scheduledReconciliation() {
        if (!drainGate.tryEnter()) {
            return;
        }
        try {
            runReconciliation();
        } finally {
            drainGate.leave();
        }
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        drainGate.beginDrain();
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
        FencedLease lease;
        try {
            lease = fencedJobLeaseService.tryAcquire(RECONCILIATION_LEASE);
        } catch (RuntimeException exception) {
            return persistFailure(mode, "FENCED_LEASE failed: " + failureReason(exception));
        }
        if (lease == null) {
            incrementCounter("reconciliation.skipped", "reason", "lease_busy");
            log.info("Reconciliation skipped: another replica owns {}", RECONCILIATION_LEASE);
            return skippedRun(mode);
        }

        try {
            String runId = uuidGenerator.newId();
            LocalDateTime startedAt = LocalDateTime.now();
            ReconciliationRun run = new ReconciliationRun();
            run.setRunId(runId);
            run.setStartedAt(startedAt);
            run.setOwner("ALL");
            run.setScanMode(mode);
            run.setScanCreatedSince(createdSince);
            run.setFenceToken(lease.fenceToken());
            run.setStatus("RUNNING");
            run.setDivergenceCount(0);
            run.setOrphanCount(0);
            runMapper.insert(run);

            List<ReconciliationResult> reconResults = new ArrayList<>();
            List<OrphanDetectionResult> orphanResults = new ArrayList<>();
            ScanProgress progress = null;
            int totalDivergence = 0;
            int totalOrphans = 0;
            String failureReason = null;

            try {
                progress = loadCheckpoint(mode, createdSince);
                orphanResults.addAll(authOrphans());
                orphanResults.add(submissionOrphans(createdSince, progress));
                orphanResults.add(notificationOrphans(createdSince, progress));
                orphanResults.addAll(appOrphans());
                orphanResults.add(auditLogsOrphans(progress));
                for (OrphanDetectionResult result : orphanResults) {
                    if (!result.isOrphanFree()) {
                        totalOrphans++;
                    }
                }

                run.setDivergenceCount(totalDivergence);
                run.setOrphanCount(totalOrphans);
                run.setStatus(progress.isComplete() ? "COMPLETED" : "PARTIAL");
            } catch (Exception exception) {
                failureReason = failureReason(exception);
                log.error("Reconciliation run {} failed: {}", runId, failureReason, exception);
                run.setStatus("FAILED");
                incrementCounter("reconciliation.failures", "mode", mode);
            }

            run.setFinishedAt(LocalDateTime.now());
            run.setDetail(buildDetailJson(mode, reconResults, orphanResults, failureReason, progress));
            if (!fencedJobLeaseService.renew(lease)) {
                run.setStatus("FAILED");
                incrementCounter("reconciliation.lease_lost", "mode", mode);
                log.error("Reconciliation run {} lost fenced lease before completion", runId);
                return run;
            }
            int updated = runMapper.updateByIdWhileLeaseHeld(
                    run, lease.name(), lease.ownerToken(), lease.fenceToken());
            if (updated != 1) {
                run.setStatus("FAILED");
                incrementCounter("reconciliation.lease_lost", "mode", mode);
                log.error("Reconciliation run {} completion rejected by fenced lease", runId);
                return run;
            }
            incrementCounter("reconciliation.runs", "mode", mode, "status", run.getStatus());
            logReconciliationResults(reconResults, orphanResults, totalDivergence, totalOrphans);
            return run;
        } finally {
            try {
                fencedJobLeaseService.release(lease);
            } catch (RuntimeException exception) {
                log.error("Unable to release reconciliation lease {}", RECONCILIATION_LEASE, exception);
            }
        }
    }

    private ReconciliationRun persistFailure(String mode, String reason) {
        String runId = uuidGenerator.newId();
        ReconciliationRun run = new ReconciliationRun();
        run.setRunId(runId);
        run.setStartedAt(LocalDateTime.now());
        run.setOwner("ALL");
        run.setScanMode(mode);
        run.setScanCreatedSince(null);
        run.setStatus("RUNNING");
        run.setDivergenceCount(0);
        run.setOrphanCount(0);
        runMapper.insert(run);
        run.setFinishedAt(LocalDateTime.now());
        run.setStatus("FAILED");
        run.setDetail(buildDetailJson(mode, List.of(), List.of(), reason, null));
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

    private ScanProgress loadCheckpoint(String mode, LocalDateTime createdSince) {
        String expectedCreatedSince = createdSince == null ? null : createdSince.toString();
        ReconciliationRun partial = runMapper.findLatestPartial(mode, createdSince);
        if (partial == null) {
            return ScanProgress.initial(createdSince);
        }
        ReconciliationRun completed = runMapper.findLatestCompleted(mode, createdSince);
        if (isSuperseded(partial, completed)) {
            return ScanProgress.initial(createdSince);
        }
        try {
            JsonNode detail = objectMapper.readTree(partial.getDetail());
            if (detail == null || !mode.equals(checkpointText(detail, "mode"))) {
                throw new IllegalStateException("checkpoint mode does not match run mode");
            }
            JsonNode continuation = checkpointObject(detail, "continuation");
            String persistedCreatedSince = checkpointNullableText(continuation, "createdSince");
            if (!Objects.equals(persistedCreatedSince, expectedCreatedSince)) {
                return ScanProgress.initial(createdSince);
            }
            return ScanProgress.from(continuation, persistedCreatedSince);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("invalid reconciliation checkpoint", exception);
        }
    }

    private static boolean isSuperseded(ReconciliationRun partial, ReconciliationRun completed) {
        return completed != null && partial.getStartedAt() != null
                && completed.getStartedAt() != null
                && !partial.getStartedAt().isAfter(completed.getStartedAt());
    }

    private static JsonNode checkpointObject(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isObject()) {
            throw new IllegalStateException("checkpoint field must be an object: " + field);
        }
        return value;
    }

    private static String checkpointText(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalStateException("checkpoint field must be text: " + field);
        }
        return value.textValue();
    }

    private static String checkpointNullableText(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalStateException("checkpoint field must be text or null: " + field);
        }
        return value.textValue();
    }

    private static long checkpointLong(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isIntegralNumber()
                || !value.canConvertToLong() || value.longValue() < 0) {
            throw new IllegalStateException("checkpoint field must be a non-negative integer: " + field);
        }
        return value.longValue();
    }

    private static int checkpointInt(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isIntegralNumber()
                || !value.canConvertToInt() || value.intValue() < 0) {
            throw new IllegalStateException("checkpoint field must be a non-negative integer: " + field);
        }
        return value.intValue();
    }

    private static boolean checkpointBoolean(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalStateException("checkpoint field must be boolean: " + field);
        }
        return value.booleanValue();
    }

    private static final class ScanProgress {
        private final String createdSince;
        private String submissionCursor;
        private long submissionMissing;
        private boolean submissionComplete;
        private String notificationCursor;
        private long notificationMissing;
        private boolean notificationComplete;
        private int auditOffset;
        private long auditMissing;
        private boolean auditComplete;

        private ScanProgress(String createdSince,
                             String submissionCursor, long submissionMissing,
                             boolean submissionComplete, String notificationCursor,
                             long notificationMissing, boolean notificationComplete,
                             int auditOffset, long auditMissing, boolean auditComplete) {
            this.createdSince = createdSince;
            this.submissionCursor = submissionCursor;
            this.submissionMissing = submissionMissing;
            this.submissionComplete = submissionComplete;
            this.notificationCursor = notificationCursor;
            this.notificationMissing = notificationMissing;
            this.notificationComplete = notificationComplete;
            this.auditOffset = auditOffset;
            this.auditMissing = auditMissing;
            this.auditComplete = auditComplete;
        }

        private static ScanProgress initial(LocalDateTime createdSince) {
            return new ScanProgress(createdSince == null ? null : createdSince.toString(),
                    "", 0L, false, "", 0L, false, 0, 0L, false);
        }

        private static ScanProgress from(JsonNode continuation, String createdSince) {
            JsonNode submission = checkpointObject(continuation, "submission");
            JsonNode notification = checkpointObject(continuation, "notification");
            JsonNode audit = checkpointObject(continuation, "audit");
            ScanProgress progress = new ScanProgress(createdSince,
                    checkpointText(submission, "cursor"), checkpointLong(submission, "missing"),
                    checkpointBoolean(submission, "complete"),
                    checkpointText(notification, "cursor"), checkpointLong(notification, "missing"),
                    checkpointBoolean(notification, "complete"),
                    checkpointInt(audit, "offset"), checkpointLong(audit, "missing"),
                    checkpointBoolean(audit, "complete"));
            if ((!progress.submissionComplete && progress.submissionCursor.isBlank())
                    || (!progress.notificationComplete && progress.notificationCursor.isBlank())) {
                throw new IllegalStateException("incomplete checkpoint is missing a cursor");
            }
            return progress;
        }

        private boolean isComplete() {
            return submissionComplete && notificationComplete && auditComplete;
        }
    }

    /** Four Auth-internal orphan checks via the auth Dubbo provider. */
    private List<OrphanDetectionResult> authOrphans() {
        if (authQueryService == null) {
            throw authUnavailable();
        }
        RpcResult<AuthReconciliationOrphanCounts> result = authQueryService.countAuthOrphans();
        OwnerRead<AuthReconciliationOrphanCounts> read =
                AdminReadContract.classify("Auth", result);
        if (!read.available() || read.value() == null) {
            throw authUnavailable();
        }
        AuthReconciliationOrphanCounts counts = read.value();
        return List.of(
                orphan("refresh_tokens", "user_id", "Auth", "users", "Auth", counts.refreshTokens()),
                orphan("password_resets", "user_id", "Auth", "users", "Auth", counts.passwordResets()),
                orphan("oauth_provider_identities", "user_id", "Auth", "users", "Auth", counts.oauthProviderIdentities()),
                orphan("user_permissions", "user_id", "Auth", "users", "Auth", counts.userPermissions()));
    }

    /** Bounded Submission-owned orphan scan for full or incremental runs. */
    private OrphanDetectionResult submissionOrphans(
            LocalDateTime createdSince, ScanProgress progress) {
        if (submissionReconciliationReadPort == null) {
            throw submissionUnavailable();
        }
        if (progress.submissionComplete) {
            return orphan("submissions", "user_id", "Submission", "users", "Auth",
                    progress.submissionMissing);
        }
        OrphanScan.KeysetResult scan;
        try {
            scan = OrphanScan.keyset(
                    progress.submissionCursor,
                    RECONCILIATION_PAGE_SIZE,
                    MAX_RECONCILIATION_PAGES,
                    (after, limit) -> submissionReconciliationReadPort.findUserReferenceCounts(
                            after, createdSince, limit),
                    SubmissionUserReferenceCountDTO::accountId,
                    SubmissionUserReferenceCountDTO::rowCount,
                    this::existingUserIds);
        } catch (OrphanScan.InvalidPageException exception) {
            throw submissionUnavailable();
        }
        progress.submissionMissing = Math.addExact(progress.submissionMissing, scan.missing());
        progress.submissionCursor = scan.nextCursor();
        progress.submissionComplete = scan.complete();
        return orphan("submissions", "user_id", "Submission", "users", "Auth",
                progress.submissionMissing);
    }

    /** Bounded Notification-owned orphan scan for full or incremental runs. */
    private OrphanDetectionResult notificationOrphans(
            LocalDateTime createdSince, ScanProgress progress) {
        if (notificationReconciliationReadPort == null) {
            throw notificationUnavailable();
        }
        if (progress.notificationComplete) {
            return orphan("notifications", "user_id", "Notification", "users", "Auth",
                    progress.notificationMissing);
        }
        OrphanScan.KeysetResult scan;
        try {
            scan = OrphanScan.keyset(
                    progress.notificationCursor,
                    NOTIFICATION_RECONCILIATION_PAGE_SIZE,
                    MAX_RECONCILIATION_PAGES,
                    (after, limit) -> notificationReconciliationReadPort.findUserReferenceCounts(
                            after, createdSince, limit),
                    NotificationUserReferenceCountDTO::accountId,
                    NotificationUserReferenceCountDTO::rowCount,
                    this::existingUserIds);
        } catch (OrphanScan.InvalidPageException exception) {
            throw notificationUnavailable();
        }
        progress.notificationMissing = Math.addExact(progress.notificationMissing, scan.missing());
        progress.notificationCursor = scan.nextCursor();
        progress.notificationComplete = scan.complete();
        return orphan("notifications", "user_id", "Notification", "users", "Auth",
                progress.notificationMissing);
    }

    /** Seven App child orphan checks; Submission and Notification rows are owner facts above. */
    private List<OrphanDetectionResult> appOrphans() {
        ReconciliationOrphanCounts counts = appReconciliationReadPort.countOrphans();
        return List.of(
                orphan("solutions", "user_id", "App", "users", "Auth", counts.solutions()),
                orphan("forum_posts", "user_id", "App", "users", "Auth", counts.forumPosts()),
                orphan("user_profiles", "account_id", "App", "users", "Auth", counts.userProfiles()),
                orphan("contest_participants", "user_id", "App", "users", "Auth", counts.contestParticipants()),
                orphan("user_achievements", "user_id", "App", "users", "Auth", counts.userAchievements()),
                orphan("user_follows", "follower_id", "App", "users", "Auth", counts.userFollowsByFollower()),
                orphan("user_follows", "following_id", "App", "users", "Auth", counts.userFollowsByFollowing()));
    }

    /** Admin-local audit_logs candidates checked against Auth physical existence in bounded pages. */
    private OrphanDetectionResult auditLogsOrphans(ScanProgress progress) {
        final int pageSize = 500;
        if (progress.auditComplete) {
            return orphan("audit_logs", "performer_id", "Admin", "users", "Auth",
                    progress.auditMissing);
        }
        OrphanScan.OffsetResult scan;
        try {
            scan = OrphanScan.offset(
                    pageSize,
                    MAX_RECONCILIATION_PAGES,
                    (offset, limit) -> {
                        try {
                            return auditOrphanMapper.auditPerformerIds(
                                    Math.addExact(offset, progress.auditOffset), limit);
                        } catch (ArithmeticException exception) {
                            throw new OrphanScan.InvalidPageException(
                                    "orphan scan offset exceeds integer range");
                        }
                    },
                    AuditReferenceCount::getPerformerId,
                    AuditReferenceCount::getRowCount,
                    this::existingUserIds);
        } catch (OrphanScan.InvalidPageException exception) {
            throw new BusinessException(
                    BaseErrorCode.UNKNOWN_ERROR, "Admin audit reconciliation page unavailable");
        }
        progress.auditMissing = Math.addExact(progress.auditMissing, scan.missing());
        progress.auditOffset = Math.addExact(progress.auditOffset, scan.nextOffset());
        progress.auditComplete = scan.complete();
        return orphan("audit_logs", "performer_id", "Admin", "users", "Auth",
                progress.auditMissing);
    }

    private Set<String> existingUserIds(Set<String> candidates) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        if (authQueryService == null) {
            throw authUnavailable();
        }
        RpcResult<Set<String>> result = authQueryService.existingUserIds(candidates);
        OwnerRead<Set<String>> read = AdminReadContract.classify("Auth", result);
        if (!read.available() || read.value() == null) {
            throw authUnavailable();
        }
        Set<String> existing = new HashSet<>();
        existing.addAll(read.value());
        return existing;
    }

    private BusinessException authUnavailable() {
        return AdminReadContract.ownerUnavailable("Auth");
    }
    private BusinessException submissionUnavailable() {
        return AdminReadContract.ownerUnavailable("Submission");
    }
    private BusinessException notificationUnavailable() {
        return AdminReadContract.ownerUnavailable("Notification");
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
                                   String failureReason,
                                   ScanProgress progress) {
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
        if (failureReason == null && progress != null) {
            appendContinuation(sb, progress);
        }
        if (failureReason != null) {
            sb.append(",\"error\":\"").append(jsonEscape(failureReason)).append("\"");
        }
        return sb.append("}").toString();
    }

    private static void appendContinuation(StringBuilder sb, ScanProgress progress) {
        sb.append(",\"continuation\":{\"createdSince\":");
        if (progress.createdSince == null) {
            sb.append("null");
        } else {
            sb.append("\"").append(jsonEscape(progress.createdSince)).append("\"");
        }
        sb.append(",\"submission\":{\"cursor\":\"")
                .append(jsonEscape(progress.submissionCursor))
                .append("\",\"missing\":").append(progress.submissionMissing)
                .append(",\"complete\":").append(progress.submissionComplete).append("}")
                .append(",\"notification\":{\"cursor\":\"")
                .append(jsonEscape(progress.notificationCursor))
                .append("\",\"missing\":").append(progress.notificationMissing)
                .append(",\"complete\":").append(progress.notificationComplete).append("}")
                .append(",\"audit\":{\"offset\":").append(progress.auditOffset)
                .append(",\"missing\":").append(progress.auditMissing)
                .append(",\"complete\":").append(progress.auditComplete).append("}}");
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
