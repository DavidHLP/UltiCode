package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.*;
import com.ulticode.modules.admin.service.AdminSubmissionService;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.queue.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.queue.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.common.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.fence.SubmissionStateMachine;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.admin.port.AdminSubmissionReadPort;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of AdminSubmissionService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSubmissionServiceImpl implements AdminSubmissionService {

    private final SubmissionMapper submissionMapper;
    private final AdminSubmissionReadPort submissionReadPort;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;
    private final QueueService queueService;
    /**
     * ADR-003 M3a outbox mapper for the rejudge double-write. Nullable so the
     * flag-off path (no outbox wiring in legacy tests) is unaffected.
     *
     * <p>P0 #11: under port cutover, the row becomes a <b>real</b> outbox row
     * for the dispatcher (port mode writes {@code is_shadow=0}, not
     * {@code is_shadow=1} as in the original M3a shadow double-write).
     * See {@link #writeRejudgeOutbox}.
     */
    private final JudgeOutboxMapper judgeOutboxMapper;
    private final FeatureFlagsProperties featureFlags;
    /**
     * Programmatic transaction boundary for the M3b fenced rejudge path. Used
     * instead of {@code @Transactional} so the flag-off branch can stay
     * byte-for-byte identical to the legacy non-transactional rejudge.
     */
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Override
    public PageResult<AdminSubmissionVO> getSubmissions(AdminSubmissionQueryDTO query) {
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int limit = query.getLimit() != null && query.getLimit() > 0 ? Math.min(query.getLimit(), 100) : 10;

        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();

        // Search filter — resolve at DB level by pre-fetching matching user/problem IDs
        if (StringUtils.hasText(query.getSearch())) {
            String search = query.getSearch();

            // Find user IDs matching the search term
            List<String> matchingUserIds = userMapper.selectList(
                    new LambdaQueryWrapper<User>().like(User::getUsername, search)
            ).stream().map(User::getId).collect(Collectors.toList());

            // Find problem IDs matching the search term
            List<Long> matchingProblemIds = problemMapper.selectList(
                    new LambdaQueryWrapper<Problem>().like(Problem::getTitle, search)
            ).stream().map(Problem::getId).collect(Collectors.toList());

            wrapper.and(w -> {
                w.like(Submission::getId, search)
                        .or().eq(Submission::getLanguage, search);
                if (!matchingUserIds.isEmpty()) {
                    w.or().in(Submission::getUserId, matchingUserIds);
                }
                if (!matchingProblemIds.isEmpty()) {
                    w.or().in(Submission::getProblemId, matchingProblemIds);
                }
            });
        }

        // User ID filter
        if (StringUtils.hasText(query.getUserId())) {
            wrapper.eq(Submission::getUserId, query.getUserId());
        }

        // Problem ID filter
        if (query.getProblemId() != null) {
            wrapper.eq(Submission::getProblemId, query.getProblemId());
        }

        // Status filter
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(Submission::getStatus, query.getStatus());
        }

        // Language filter
        if (StringUtils.hasText(query.getLanguage())) {
            wrapper.eq(Submission::getLanguage, query.getLanguage());
        }

        // Date range filter
        if (query.getStartDate() != null) {
            wrapper.ge(Submission::getCreatedAt, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(Submission::getCreatedAt, query.getEndDate());
        }

        // Sorting
        boolean isAsc = !"desc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "createdAt";
        switch (sortBy) {
            case "createdAt" -> wrapper.orderBy(true, isAsc, Submission::getCreatedAt);
            case "runtime" -> wrapper.orderBy(true, isAsc, Submission::getRuntime);
            case "memory" -> wrapper.orderBy(true, isAsc, Submission::getMemory);
            case "status" -> wrapper.orderBy(true, isAsc, Submission::getStatus);
            default -> wrapper.orderBy(true, isAsc, Submission::getCreatedAt);
        }

        Page<Submission> pageResult = new Page<>(page, limit);
        Page<Submission> result = submissionMapper.selectPage(pageResult, wrapper);

        // Batch-load users and problems to avoid N+1 queries (WR-05)
        Map<String, User> userMap = new HashMap<>();
        Map<Long, Problem> problemMap = new HashMap<>();
        if (!result.getRecords().isEmpty()) {
            Set<String> userIds = result.getRecords().stream()
                    .map(Submission::getUserId)
                    .collect(Collectors.toSet());
            Set<Long> problemIds = result.getRecords().stream()
                    .map(Submission::getProblemId)
                    .collect(Collectors.toSet());

            if (!userIds.isEmpty()) {
                userMap = userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
            }
            if (!problemIds.isEmpty()) {
                problemMap = problemMapper.selectBatchIds(problemIds).stream()
                        .collect(Collectors.toMap(Problem::getId, p -> p));
            }
        }

        // Enrich with user and problem information using batch-loaded maps
        Map<String, User> finalUserMap = userMap;
        Map<Long, Problem> finalProblemMap = problemMap;
        List<AdminSubmissionVO> vos = result.getRecords().stream()
                .map(s -> toAdminVO(s, finalUserMap, finalProblemMap))
                .collect(Collectors.toList());

        // All filtering now at DB level — use database total for correct pagination
        return PageResult.of(
                vos,
                result.getTotal(),
                page,
                limit
        );
    }

    @Override
    public AdminSubmissionVO getSubmission(String id) {
        Submission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }
        return toAdminVOWithDetails(submission);
    }

    @Override
    public SubmissionStatistics getStatistics() {
        SubmissionStatistics stats = new SubmissionStatistics();

        // Total submissions — via the typed read port (no mapper leak)
        stats.setTotal(submissionReadPort.countAll());

        // By status — typed projection from the read port
        List<SubmissionStatistics.StatusCount> byStatus = new ArrayList<>();
        for (com.ulticode.modules.submission.dto.StatusCountDTO row : submissionReadPort.countByStatus()) {
            SubmissionStatistics.StatusCount sc = new SubmissionStatistics.StatusCount();
            sc.setStatus(row.getStatus());
            sc.setCount(row.getCount());
            byStatus.add(sc);
        }
        stats.setByStatus(byStatus);

        // By language — typed projection from the read port
        List<SubmissionStatistics.LanguageCount> byLanguage = new ArrayList<>();
        for (com.ulticode.modules.submission.dto.LanguageCountDTO row : submissionReadPort.countByLanguage()) {
            SubmissionStatistics.LanguageCount lc = new SubmissionStatistics.LanguageCount();
            lc.setLanguage(row.getLanguage());
            lc.setCount(row.getCount());
            byLanguage.add(lc);
        }
        stats.setByLanguage(byLanguage);

        // Last 24 hours
        LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
        Long last24h = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>().ge(Submission::getCreatedAt, yesterday)
        );
        stats.setLast24h(last24h);

        // Pending count
        Long pending = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>().eq(Submission::getStatus, "Pending")
        );
        stats.setPending(pending);

        return stats;
    }

    @Override
    public List<StatusOption> getStatuses() {
        // Derive filter options from the canonical enum so the dropdown
        // stays in sync with both the DB (displayName) and statistics
        // (category). Returns all 11 statuses including transient ones
        // (Judging) so admins can see and filter on every observed state.
        return Arrays.stream(SubmissionStatus.values())
            .map(s -> {
                StatusOption opt = new StatusOption();
                opt.setKey(s.getDisplayName());
                opt.setLabel(s.getDisplayName());
                opt.setCode(s.name());
                opt.setCategory(s.getCategory());
                return opt;
            })
            .collect(Collectors.toList());
    }

    @Override
    public List<LanguageOption> getLanguages() {
        return submissionMapper.findDistinctLanguages().stream()
            .map(code -> {
                LanguageOption opt = new LanguageOption();
                opt.setKey(code);
                opt.setLabel(humanizeLanguage(code));
                return opt;
            })
            .collect(Collectors.toList());
    }

    /**
     * Convert a language code stored in the database to a human-readable
     * display label. Falls back to title-cased code for unknown languages.
     *
     * @param code DB-stored language code (e.g. {@code "cpp"})
     * @return display label (e.g. {@code "C++"})
     */
    private String humanizeLanguage(String code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case "cpp" -> "C++";
            case "c" -> "C";
            case "csharp" -> "C#";
            case "java" -> "Java";
            case "python" -> "Python";
            case "javascript" -> "JavaScript";
            case "typescript" -> "TypeScript";
            case "go" -> "Go";
            case "rust" -> "Rust";
            case "ruby" -> "Ruby";
            case "kotlin" -> "Kotlin";
            case "swift" -> "Swift";
            default -> code.substring(0, 1).toUpperCase() + code.substring(1);
        };
    }

    @Override
    @Audited(action = AuditActionUtil.REQUEUE_SUBMISSION, entityType = AuditActionUtil.ENTITY_SUBMISSION, userIdFrom = "id")
    public RejudgeResult rejudge(String id, boolean notifyUser) {
        Submission submission = submissionMapper.selectById(id);
        if (submission == null) {
            RejudgeResult result = new RejudgeResult();
            result.setSubmissionId(id);
            result.setSuccess(false);
            result.setError("Submission not found");
            return result;
        }

        RejudgeResult result = new RejudgeResult();
        result.setSubmissionId(id);
        result.setOldStatus(submission.getStatus());

        // ADR-003 M3b: fenced rejudge path. When the generation fence flag is
        // off, fall through to the legacy non-transactional path so behavior is
        // byte-for-byte identical to the pre-fence implementation.
        if (!featureFlags.isUseGenerationFence()) {
            return rejudgeLegacy(submission, result);
        }
        return rejudgeFenced(submission, result);
    }

    /**
     * Legacy rejudge path (pre-ADR-003). Non-transactional; on enqueue failure
     * the DB row stays Pending (orphan), matching the historical contract.
     * Preserved verbatim so flag-off deployments observe no behavior change.
     */
    private RejudgeResult rejudgeLegacy(Submission submission, RejudgeResult result) {
        String id = submission.getId();
        try {
            // Reset submission status to Pending for re-evaluation
            submission.setStatus("Pending");

            // D-23: Increment retry count to track rejudge attempts
            submission.setRetryCount(
                submission.getRetryCount() != null ? submission.getRetryCount() + 1 : 1
            );
            submissionMapper.updateById(submission);

            // D-04: Enqueue after DB update to avoid orphaned jobs on DB failure
            queueService.enqueueJudgeJob(
                submission.getId(),
                String.valueOf(submission.getProblemId()),
                submission.getUserId(),
                submission.getLanguage(),
                submission.getCode()
            );

            result.setSuccess(true);
            result.setNewStatus("Pending");
            // Surface rejudge metadata to the caller so the admin UI can
            // detect that a rejudge actually happened even when old and
            // new status are identical (e.g. Pending -> Pending).
            result.setRejudgedAt(Instant.now());
            result.setRetryCount(submission.getRetryCount());
            log.info("Rejudge initiated for submission: {} (retryCount={})",
                id, submission.getRetryCount());
        // broad catch: all failures map to same error response
        } catch (Exception e) {
            log.error("Failed to enqueue rejudge for submission: {}", id, e);
            result.setSuccess(false);
            result.setError(e.getMessage());
        }

        if (result.getSuccess()) {
            AuditContext.setOldValues(java.util.Map.of(
                "oldStatus", result.getOldStatus() != null ? result.getOldStatus() : "",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
            AuditContext.setNewValues(java.util.Map.of(
                "newStatus", "Pending",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
        }

        return result;
    }

    /**
     * ADR-003 M3b fenced rejudge path. Runs the generation bump + outbox
     * double-write inside a single transaction (F7); the Redis enqueue is
     * deferred to an {@code afterCommit} callback (F3 fix) so a worker cannot
     * consume the job before the Pending/generation update commits.
     *
     * <p>Branching on observed status:
     * <ul>
     *   <li>Terminal status + {@link SubmissionStateMachine#canAdminRejudgeFrom}
     *       -> bump generation, reset to Pending, outbox. <b>F1:</b> retry_count
     *       is persisted via the targeted {@code bumpRetryCount} CAS — the
     *       branch must NOT call {@code updateById(submission)} because the
     *       in-memory entity still carries the stale terminal status + the
     *       pre-bump generation, and MyBatis-Plus's default {@code NOT_NULL}
     *       strategy would write them back over the Pending reset + new gen,
     *       leaving the worker unable to acquire its Pending lease. If the bump
     *       CAS loses a race ({@code bumped == 0}) the branch does NOT enqueue
     *       and returns a conflict (the winning writer owns the dispatch).</li>
     *   <li>JUDGING -> force lease expiry + revoke the attempt (F2); the lease
     *       reaper will atomically bump generation in its single transaction.
     *       This avoids racing the worker on the generation field.</li>
     *   <li>Other (e.g. already Pending) -> outbox without a bump.</li>
     * </ul>
     */
    private RejudgeResult rejudgeFenced(Submission submission, RejudgeResult result) {
        String id = submission.getId();
        // Tracks whether the DB mutation branch actually won the right to
        // dispatch. The JUDGING branch never dispatches here (the reaper does).
        // A bump-CAS loss means another writer already dispatched; we surface a
        // conflict rather than enqueueing a duplicate the fence would drop.
        final boolean[] dispatchWon = { false };
        try {
            transactionTemplate.executeWithoutResult(status -> {
                SubmissionStatus current = SubmissionStatus.fromDbName(submission.getStatus());
                boolean judging = current == SubmissionStatus.JUDGING;
                boolean rejudgeable = SubmissionStateMachine.canAdminRejudgeFrom(current);

                // Increment retry count regardless of branch (matches legacy). It
                // is persisted via the targeted bumpRetryCount CAS below — never
                // via updateById(submission), which would clobber the fence
                // columns (F1, C1).
                submission.setRetryCount(
                    submission.getRetryCount() != null ? submission.getRetryCount() + 1 : 1
                );

                if (judging) {
                    // Force the lease to expire AND revoke the active attempt (F2);
                    // the reaper will bump generation atomically. We do NOT bump
                    // here to avoid racing the worker.
                    if (submission.getCurrentAttemptId() != null) {
                        submissionMapper.forceLeaseExpiry(id, submission.getCurrentAttemptId());
                    }
                    // C1 fix: persist retry_count via a TARGETED update that touches
                    // ONLY retry_count. We must NOT call updateById here: the entity
                    // still holds the original future lease value read at rejudge()
                    // line 294 (forceLeaseExpiry ran via a DB CAS and did not refresh
                    // the entity), and MyBatis-Plus's default NOT_NULL update strategy
                    // would write that stale future lease back, silently undoing the
                    // forced expiry and leaving the row stuck in JUDGING forever.
                    // Status stays Judging until the reaper flips it to Pending.
                    submissionMapper.bumpRetryCount(id, 1);
                    // JUDGING branch never dispatches here — the reaper bumps the
                    // generation and dispatches from its own single transaction.
                    // Outbox write is intentionally SKIPPED (see H2 note below).
                } else if (rejudgeable) {
                    // Terminal -> bump generation atomically, reset to Pending.
                    long expectedGen = submission.getGeneration() != null ? submission.getGeneration() : 1L;
                    long newGen = expectedGen + 1;
                    int bumped = submissionMapper.bumpGenerationAndReset(id, expectedGen, newGen);
                    boolean bumpWon = bumped == 1;
                    // F1 fix: retry_count via targeted CAS — NOT updateById. The
                    // bumpGenerationAndReset CAS already set status='Pending' +
                    // generation=newGen + cleared lease columns in the DB; an
                    // updateById(submission) here would write the entity's stale
                    // terminal status + old generation back (NOT_NULL strategy),
                    // un-doing the Pending reset so the worker's acquireLease
                    // (WHERE status='Pending') fails and the rejudge is lost.
                    submissionMapper.bumpRetryCount(id, 1);
                    if (bumpWon) {
                        submission.setGeneration(newGen);
                        // H2 fix: only write the outbox shadow when the generation
                        // bump actually won. A race-lost bump means the row's real
                        // generation already moved; the winning writer owns the
                        // outbox row at the new gen.
                        writeRejudgeOutbox(submission, newGen);
                        dispatchWon[0] = true;
                    } else {
                        // F1: the bump CAS lost a race (another reaper / rejudge
                        // already bumped). Do NOT enqueue — the winning writer is
                        // responsible for the dispatch. Throwing here rolls back
                        // the transaction (including the retry_count bump) and
                        // surfaces a conflict to the caller, so no orphan job is
                        // created and no duplicate outbox row pollutes the diff.
                        throw new org.springframework.transaction.TransactionSystemException(
                            "concurrent generation change for submission " + id
                            + " (expected gen " + expectedGen + " already moved)");
                    }
                } else {
                    // Already Pending (or unknown): outbox without a generation bump.
                    // F1: persist retry_count via the targeted CAS, NOT updateById.
                    submissionMapper.bumpRetryCount(id, 1);
                    submission.setStatus("Pending");
                    writeRejudgeOutbox(submission,
                        submission.getGeneration() != null ? submission.getGeneration() : 1L);
                    dispatchWon[0] = true;
                }

                // F3 fix: defer the Redis enqueue to afterCommit. Enqueueing
                // inside the DB transaction let a worker consume the job before
                // the Pending/generation update committed: the worker would read
                // the stale terminal row, fail acquireLease, and permanently
                // discard the only job. Moving the enqueue to afterCommit
                // (mirroring the lease reaper's H1 pattern) guarantees the
                // worker only ever sees the post-commit Pending row. The outbox
                // insert above stays in-tx so the dispatch intent is durable.
                if (dispatchWon[0]
                        && org.springframework.transaction.support.TransactionSynchronizationManager
                                .isSynchronizationActive()) {
                    final Submission enqueueTarget = submission;
                    org.springframework.transaction.support.TransactionSynchronizationManager
                        .registerSynchronization(
                            new org.springframework.transaction.support.TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    try {
                                        queueService.enqueueJudgeJob(
                                            enqueueTarget.getId(),
                                            String.valueOf(enqueueTarget.getProblemId()),
                                            enqueueTarget.getUserId(),
                                            enqueueTarget.getLanguage(),
                                            enqueueTarget.getCode()
                                        );
                                    } catch (Exception e) {
                                        // Redis down post-commit: the DB bump +
                                        // outbox row already committed, so the row
                                        // is recoverable by the reaper / a future
                                        // outbox replay. Log and continue so one
                                        // bad enqueue does not abort the rejudge.
                                        log.warn("Post-commit enqueue failed for submission {} "
                                                + "(Pending reset committed; outbox row recorded "
                                                + "for replay): {}",
                                                enqueueTarget.getId(), e.getMessage());
                                    }
                                }
                            });
                } else if (dispatchWon[0]) {
                    // No active transaction synchronization (e.g. a direct unit-test
                    // call without a Spring tx manager): enqueue immediately so the
                    // behavior is not silently lost (matches the reaper's fallback).
                    queueService.enqueueJudgeJob(
                        submission.getId(),
                        String.valueOf(submission.getProblemId()),
                        submission.getUserId(),
                        submission.getLanguage(),
                        submission.getCode()
                    );
                }
                // JUDGING-branch outbox note (H2): we do NOT write an outbox row
                // here because the submission's generation has not been bumped
                // (the reaper does that). The only correct outbox row would be
                // at the post-reaper generation, which the reaper itself writes.
            });

            result.setSuccess(true);
            result.setNewStatus(judgingAfterRejudge(submission) ? "Judging" : "Pending");
            result.setRejudgedAt(Instant.now());
            result.setRetryCount(submission.getRetryCount());
            log.info("Fenced rejudge initiated for submission {} (retryCount={}, gen={})",
                id, submission.getRetryCount(),
                submission.getGeneration() != null ? submission.getGeneration() : 1L);
        } catch (Exception e) {
            log.error("Failed fenced rejudge for submission: {}", id, e);
            result.setSuccess(false);
            result.setError(e.getMessage());
        }

        if (result.getSuccess()) {
            AuditContext.setOldValues(java.util.Map.of(
                "oldStatus", result.getOldStatus() != null ? result.getOldStatus() : "",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
            AuditContext.setNewValues(java.util.Map.of(
                "newStatus", result.getNewStatus() != null ? result.getNewStatus() : "Pending",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
        }

        return result;
    }

    /**
     * Whether the submission is in (or will be observed as) JUDGING after the
     * fenced rejudge. Used to surface an accurate newStatus to the caller; the
     * JUDGING branch keeps the row Judging until the reaper flips it.
     */
    private boolean judgingAfterRejudge(Submission submission) {
        SubmissionStatus current = SubmissionStatus.fromDbName(submission.getStatus());
        return current == SubmissionStatus.JUDGING;
    }

    /**
     * Write a shadow outbox row for a fenced rejudge dispatch, gated on the
     * outbox feature flag and a non-null mapper (ADR-003 M3a double-write). The
     * caller must pass the <b>post-mutation</b> generation so the recorded
     * dispatch intent matches the real delivery generation (H2 fix: a stale
     * pre-bump generation would pollute the M3c shadow-comparator "diff=0"
     * gate).
     *
     * <p>The unique key {@code (submission_id, generation)} makes a duplicate
     * insert (e.g. a concurrent rejudge that already wrote this gen) throw
     * rather than silently double-record; that exception propagates and rolls
     * the transaction, which is the desired fail-loud behavior for a real
     * invariant violation.
     *
     * @param submission the submission (post-mutation, with the correct generation)
     * @param generation the post-mutation generation to record
     */
    private void writeRejudgeOutbox(Submission submission, long generation) {
        if (featureFlags.isUseJudgeOutbox() && judgeOutboxMapper != null) {
            // P0 #11 fix: `is_shadow = !portActive`. Under port cutover
            // the dispatcher ignores shadow rows, so a hard-coded `true`
            // would strand the rejudged submission Pending forever. Port
            // mode now writes a real row the dispatcher enqueues; shadow
            // mode keeps the original double-write observation behaviour.
            boolean portActive = featureFlags.getJudgeQueue().isUsePort();
            judgeOutboxMapper.insert(JudgeOutboxRecord.forResubmission(
                    submission, String.valueOf(submission.getProblemId()), generation, !portActive));
            // Canary observability (P0 #11): log every successful admin
            // rejudge outbox insert with is_shadow / portActive pair. Logged
            // AFTER the insert call so failures (unique key conflicts) stay
            // on the existing log.warn path; do NOT move inside any
            // try/catch above.
            log.info("admin_rejudge.outbox.insert submissionId={} problemId={} generation={} is_shadow={} portActive={}",
                    submission.getId(), submission.getProblemId(), generation, !portActive, portActive);
        }
    }

    @Override
    public BatchRejudgeResponse batchRejudge(List<String> submissionIds, boolean notifyUsers) {
        // Non-null, non-empty, and size<=50 are enforced by Bean Validation
        // on the controller (see BatchRejudgeRequest @NotEmpty/@Size and
        // @Valid on the @RequestBody), so we can drop the silent null/empty
        // branch that previously masked client bugs.
        BatchRejudgeResponse response = new BatchRejudgeResponse();
        response.setTotal(submissionIds.size());
        response.setResults(new ArrayList<>(submissionIds.size()));
        int successful = 0;
        int failed = 0;

        for (String id : submissionIds) {
            RejudgeResult result = rejudge(id, notifyUsers);
            response.getResults().add(result);
            if (result.getSuccess()) {
                successful++;
            } else {
                failed++;
            }
        }

        response.setSuccessful(successful);
        response.setFailed(failed);
        return response;
    }

    /**
     * Convert Submission entity to AdminSubmissionVO using pre-loaded maps (batch).
     */
    private AdminSubmissionVO toAdminVO(Submission submission, Map<String, User> userMap, Map<Long, Problem> problemMap) {
        if (submission == null) {
            return null;
        }

        AdminSubmissionVO vo = new AdminSubmissionVO();
        vo.setId(submission.getId());
        vo.setProblemId(submission.getProblemId());
        vo.setUserId(submission.getUserId());
        vo.setLanguage(submission.getLanguage());
        vo.setStatus(submission.getStatus());
        vo.setRuntime(submission.getRuntime());
        vo.setMemory(submission.getMemory());
        vo.setCreatedAt(submission.getCreatedAt());
        vo.setCodeLength(submission.getCode() != null ? submission.getCode().length() : 0);

        User user = userMap.get(submission.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
        }

        Problem problem = problemMap.get(submission.getProblemId());
        if (problem != null) {
            vo.setProblemTitle(problem.getTitle());
            vo.setProblemSlug(problem.getSlug());
        }

        return vo;
    }

    /**
     * Convert Submission entity to AdminSubmissionVO (list view).
     */
    private AdminSubmissionVO toAdminVO(Submission submission) {
        if (submission == null) {
            return null;
        }

        AdminSubmissionVO vo = new AdminSubmissionVO();
        vo.setId(submission.getId());
        vo.setProblemId(submission.getProblemId());
        vo.setUserId(submission.getUserId());
        vo.setLanguage(submission.getLanguage());
        vo.setStatus(submission.getStatus());
        vo.setRuntime(submission.getRuntime());
        vo.setMemory(submission.getMemory());
        vo.setCreatedAt(submission.getCreatedAt());

        // Calculate code length
        vo.setCodeLength(submission.getCode() != null ? submission.getCode().length() : 0);

        // Fetch user info
        User user = userMapper.selectById(submission.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
        }

        // Fetch problem info
        Problem problem = problemMapper.selectById(submission.getProblemId());
        if (problem != null) {
            vo.setProblemTitle(problem.getTitle());
            vo.setProblemSlug(problem.getSlug());
        }

        return vo;
    }

    /**
     * Convert Submission entity to AdminSubmissionVO with full details.
     */
    private AdminSubmissionVO toAdminVOWithDetails(Submission submission) {
        AdminSubmissionVO vo = toAdminVO(submission);
        if (vo != null) {
            vo.setCode(submission.getCode());
            vo.setNotes(submission.getNotes());
            vo.setRuntimePercentile(submission.getRuntimePercentile());
            vo.setMemoryPercentile(submission.getMemoryPercentile());
            vo.setTestDetails(submission.getTestDetails());
            vo.setMemoryDistBinsMb(submission.getMemoryDistBinsMb());
            vo.setRuntimeDistBinsMs(submission.getRuntimeDistBinsMs());
        }
        return vo;
    }

}
