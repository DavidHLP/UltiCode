package com.ulticode.modules.reconciliation;

import com.ulticode.common.uuid.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Nightly reconciliation job and orphan scanner (P5-RECONCILE-001).
 *
 * <p>Runs per Owner aggregate to report row-count divergence between dual-write tables
 * and detect dangling cross-owner references. The job is manifest-driven:
 * reconciliation pairs and cross-owner references are defined as static data lists
 * (mirroring {@code CROSS_OWNER_REFERENCES.md}), not discovered at runtime.
 *
 * <h2>Soft-delete semantics</h2>
 * The orphan scanner treats a reference as orphaned if the child row references a
 * parent id that does not exist <em>at all</em> in the parent table. A soft-deleted
 * parent (is_deleted=1) still physically exists and is NOT an orphan — the child
 * retains a valid logical reference until the parent is physically purged.
 *
 * <p>In the monolith deployment, the job covers all Owners in a single scheduled run.
 * When services are split into independent deployments (Phase 6/7), each service can
 * instantiate this component scoped to its own Owner tables only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerReconciler {

    private final JdbcTemplate jdbcTemplate;
    private final ReconciliationRunMapper runMapper;
    private final UuidGenerator uuidGenerator;

    /** Validates SQL identifiers to prevent injection if manifest ever becomes external. */
    private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z0-9_]+$");

    // ── Reconciliation pairs (source → target, vertical-split dual-write) ──

    record ReconciliationPair(String sourceTable, String targetTable, String owner) {}

    private static final List<ReconciliationPair> RECONCILIATION_PAIRS = List.of(
        new ReconciliationPair("users", "user_profiles", "Auth")
    );

    // ── Cross-owner orphan references ──

    record CrossOwnerRef(String childTable, String childColumn,
                         String childOwner, String parentTable, String parentOwner) {}

    private static final List<CrossOwnerRef> CROSS_OWNER_REFS = List.of(
        new CrossOwnerRef("refresh_tokens", "user_id", "Auth", "users", "Auth"),
        new CrossOwnerRef("password_resets", "user_id", "Auth", "users", "Auth"),
        new CrossOwnerRef("oauth_provider_identities", "user_id", "Auth", "users", "Auth"),
        new CrossOwnerRef("user_permissions", "user_id", "Auth", "users", "Auth"),
        new CrossOwnerRef("audit_logs", "performer_id", "Admin", "users", "Auth"),
        new CrossOwnerRef("submissions", "user_id", "App", "users", "Auth"),
        new CrossOwnerRef("solutions", "user_id", "App", "users", "Auth"),
        new CrossOwnerRef("forum_posts", "user_id", "App", "users", "Auth"),
        new CrossOwnerRef("notifications", "user_id", "App", "users", "Auth"),
        new CrossOwnerRef("user_profiles", "account_id", "App", "users", "Auth"),
        new CrossOwnerRef("contest_participants", "user_id", "App", "users", "Auth"),
        new CrossOwnerRef("user_achievements", "user_id", "App", "users", "Auth"),
        new CrossOwnerRef("user_follows", "follower_id", "App", "users", "Auth"),
        new CrossOwnerRef("user_follows", "following_id", "App", "users", "Auth")
    );

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

            for (CrossOwnerRef ref : CROSS_OWNER_REFS) {
                OrphanDetectionResult result = detectOrphans(ref);
                orphanResults.add(result);
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
     * Reconcile a single dual-write pair by comparing row counts.
     * For the vertical-split pair (users → user_profiles), counts non-deleted users
     * against the profile table.
     */
    ReconciliationResult reconcilePair(ReconciliationPair pair) {
        validateIdentifier(pair.sourceTable());
        validateIdentifier(pair.targetTable());

        Long sourceCount;
        Long targetCount;

        if ("users".equals(pair.sourceTable()) && "user_profiles".equals(pair.targetTable())) {
            sourceCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `" + pair.sourceTable() + "` WHERE `is_deleted` = 0",
                    Long.class);
            targetCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `" + pair.targetTable() + "`",
                    Long.class);
        } else {
            sourceCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `" + pair.sourceTable() + "`",
                    Long.class);
            targetCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `" + pair.targetTable() + "`",
                    Long.class);
        }

        return new ReconciliationResult(
                pair.sourceTable() + " → " + pair.targetTable(),
                pair.owner(),
                sourceCount != null ? sourceCount : 0,
                targetCount != null ? targetCount : 0);
    }

    /**
     * Detect orphaned child rows that reference a non-existent parent.
     *
     * <p>A reference is orphaned only if the parent id does not exist <em>at all</em>
     * in the parent table. Soft-deleted parents (is_deleted=1) still physically exist
     * and are NOT orphans — the child retains a valid logical reference.
     */
    OrphanDetectionResult detectOrphans(CrossOwnerRef ref) {
        validateIdentifier(ref.childTable());
        validateIdentifier(ref.childColumn());
        validateIdentifier(ref.parentTable());

        String sql = String.format(
                "SELECT COUNT(*) FROM `%s` c LEFT JOIN `%s` p ON c.`%s` = p.`id` " +
                "WHERE c.`%s` IS NOT NULL AND p.`id` IS NULL",
                ref.childTable(), ref.parentTable(), ref.childColumn(), ref.childColumn());

        Long orphanCount = jdbcTemplate.queryForObject(sql, Long.class);

        return new OrphanDetectionResult(
                ref.childTable(), ref.childOwner(),
                ref.childColumn(), ref.parentTable(), ref.parentOwner(),
                orphanCount != null ? orphanCount : 0);
    }

    /**
     * Reject identifiers that don't match the safe pattern.
     * Prevents SQL injection if the manifest ever becomes external (YAML/JSON).
     */
    private static void validateIdentifier(String identifier) {
        if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    "Invalid SQL identifier (only alphanumeric + underscore allowed): " + identifier);
        }
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
