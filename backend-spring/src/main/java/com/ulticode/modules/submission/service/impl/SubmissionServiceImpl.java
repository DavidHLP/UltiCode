package com.ulticode.modules.submission.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.LanguageStatsDTO;
import com.ulticode.modules.submission.dto.PerformanceStats;
import com.ulticode.modules.submission.dto.LearningProgressDTO;
import com.ulticode.modules.submission.dto.MonthlySubmissionStatsDTO;
import com.ulticode.modules.submission.dto.SubmissionDetailVO;
import com.ulticode.modules.submission.dto.SubmissionHistoryDTO;
import com.ulticode.modules.submission.dto.SubmissionListItemVO;
import com.ulticode.modules.submission.dto.SubmissionQueryDTO;
import com.ulticode.modules.submission.dto.SubmissionStatusMeta;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.dto.UserBestStats;
import com.ulticode.modules.submission.dto.WeeklyProgressDTO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.CaseScope;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.service.SubmissionService;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.queue.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.queue.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.common.config.FeatureFlagsProperties;
import com.ulticode.modules.websocket.service.RealtimeService;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.ContestSubmission;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.notification.service.NotificationDispatchService;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.submission.event.SubmissionJudgedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of SubmissionService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final SubmissionProjection submissionProjection;
    private final QueueService queueService;
    private final RealtimeService realtimeService;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final ContestMapper contestMapper;
    private final ContestParticipantMapper contestParticipantMapper;
    private final AchievementTriggerService achievementTriggerService;
    private final NotificationService notificationService;
    private final NotificationDispatchService notificationDispatchService;
    /**
     * ADR-004 M4c: typed intent dispatcher. Active when
     * {@link FeatureFlagsProperties#isUseNotificationIntent()} is true.
     * Injected here so the new path is wired; the legacy
     * {@code NotificationDispatchService} stays injected for the rollback
     * path.
     */
    private final com.ulticode.modules.notification.dispatcher.NotificationDispatcher notificationDispatcher;
    /**
     * ADR-003 M3a outbox mapper. Null-safe in tests that do not exercise the
     * outbox path; production wiring is via constructor injection.
     */
    private final JudgeOutboxMapper judgeOutboxMapper;
    private final FeatureFlagsProperties featureFlags;
    /**
     * Metrics registry for {@code judge.stale_result.dropped} (ADR-003 §5).
     * Nullable so integration tests that do not wire a registry still work;
     * a null registry means the counter is a silent no-op.
     */
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    /**
     * Publishes {@link SubmissionJudgedEvent} after the verdict-write transaction
     * commits. Consumed by
     * {@code com.ulticode.modules.contest.listener.ContestScoringListener} (a
     * {@code @TransactionalEventListener(AFTER_COMMIT)}) to apply contest scoring
     * (P0-1). The publish is fire-and-forget; an exception here is logged but
     * never propagated to the judge worker.
     */
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Supported languages for submission.
     */
    private static final List<String> SUPPORTED_LANGUAGES = List.of(
            "javascript", "python", "java", "c", "cpp"
    );

    /**
     * Number of buckets used when the distinct-value count exceeds the
     * exact-mode threshold (see {@link #buildDistributionBins}). 12 is the
     * chosen "small but readable" default for runtime/memory histograms.
     */
    private static final int DEFAULT_DISTRIBUTION_BIN_COUNT = 12;

    @Override
    @Transactional
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        // Validate user ID
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_USER_ID_REQUIRED);
        }

        // Validate code is not empty
        if (!StringUtils.hasText(createDTO.getCode())) {
            throw new BusinessException(ErrorCode.SUBMISSION_CODE_EMPTY);
        }

        // Validate language is supported
        String language = createDTO.getLanguage().toLowerCase();
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED);
        }

        // Verify problem exists
        Problem problem = problemMapper.selectById(createDTO.getProblemId());
        if (problem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }

        // Verify user exists
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Create submission with Pending status
        Submission submission = new Submission();
        submission.setId(UUID.randomUUID().toString());
        submission.setUserId(userId);
        submission.setProblemId(createDTO.getProblemId());
        submission.setLanguage(language);
        submission.setCode(createDTO.getCode());
        submission.setStatus("Pending");
        submission.setRuntime(0);
        submission.setMemory(0.0);
        submission.setCreatedAt(LocalDateTime.now());
        submission.setTestDetails(new ArrayList<>());

        // Save submission
        submissionMapper.insert(submission);

        log.info("Created submission {} for user {} and problem {}", submission.getId(), userId, createDTO.getProblemId());

        // ADR-003 M3a + M3c-2 fix: when the port cutover is active, write
        // is_shadow=false so the real-dispatch path picks the row;
        // otherwise write is_shadow=true for the M3a shadow window. The
        // legacy RQueue enqueue below is skipped when the port is active
        // so the outbox dispatcher is the sole active producer (codex
        // P1 #1 — flag-off behavior is byte-for-byte identical to the
        // legacy path).
        boolean portActive = featureFlags.getJudgeQueue().isUsePort();
        if (featureFlags.isUseJudgeOutbox() && judgeOutboxMapper != null) {
            // P1-1 (A+): outbox insert shares the @Transactional boundary with
            // submissionMapper.insert above. Let exceptions propagate so the
            // transaction rolls back — submission + outbox row live or die
            // together (ADR-003 "submission + outbox 同事务"). Previously this
            // was try-caught and logged, which left a Pending orphan when the
            // port cutover was active: port mode skips RQueue, so the outbox
            // was the sole producer and a swallowed insert failure stranded
            // the submission in Pending forever.
            long generation = submission.getGeneration() != null ? submission.getGeneration() : 1L;
            boolean isShadow = !portActive;
            judgeOutboxMapper.insert(JudgeOutboxRecord.of(
                    submission, String.valueOf(createDTO.getProblemId()), generation, isShadow));
        }

        // --- Contest submission recording (D-04, D-05, D-06) ---
        try {
            recordContestSubmissionIfNeeded(submission.getId(), userId, createDTO.getProblemId());
        } catch (Exception e) {
            log.warn("Failed to record contest submission for submission {}", submission.getId(), e);
            // Don't fail the main submission -- contest recording is supplementary
        }

        if (portActive) {
            // codex P1 #1 fix: when the port cutover is active, the outbox
            // dispatcher is the sole active producer. Skip the legacy
            // RQueue enqueue to avoid double-dispatch.
            log.debug("Submit {} skipped legacy RQueue (port cutover active)", submission.getId());
        } else {
            try {
                queueService.enqueueJudgeJob(
                    submission.getId(),
                    String.valueOf(createDTO.getProblemId()),
                    userId,
                    language,
                    createDTO.getCode());
                log.info("Enqueued judge job for submission {}", submission.getId());
            // broad catch: enqueue failure falls back to system error status
            } catch (Exception e) {
            log.error("Failed to enqueue judge job for submission {}", submission.getId(), e);
            submission.setStatus("System Error");
            submission.setNotes("Judge queue unavailable — submission was not processed");
            submissionMapper.updateById(submission);
        }
        }

        return submissionProjection.toVO(submission);
    }

    @Override
    public SubmissionDetailVO findById(String id, String userId) {
        Submission submission = submissionMapper.selectById(id);

        if (submission == null) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }

        // Access control: users can only see their own submissions
        if (StringUtils.hasText(userId) && !submission.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }

        PerformanceStats stats = PerformanceStats.EMPTY;
        if ("Accepted".equals(submission.getStatus())) {
            stats = computePerformanceStats(submission,
                    submission.getRuntime() != null ? submission.getRuntime() : 0,
                    submission.getMemory());
        }

        return submissionProjection.toDetailVO(submission, stats);
    }

    @Override
    public PageResult<SubmissionVO> findByUserId(String userId, SubmissionQueryDTO query) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_USER_ID_REQUIRED);
        }

        int page = query.getPage() != null ? query.getPage() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 10;

        Page<Submission> pageParam = new Page<>(page, pageSize);
        IPage<SubmissionMapper.SubmissionWithProblem> result =
                submissionMapper.findByUserIdWithProblem(userId, pageParam);

        List<SubmissionVO> voList = result.getRecords().stream()
                .map(submissionProjection::toVO)
                .toList();

        return PageResult.of(voList, result.getTotal(), page, pageSize);
    }

    @Override
    public PageResult<SubmissionListItemVO> findByProblemId(Long problemId, String userId, SubmissionQueryDTO query) {
        int page = query.getPage() != null ? query.getPage() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 10;

        Page<Submission> pageParam = new Page<>(page, pageSize);
        IPage<SubmissionMapper.SubmissionWithProblem> result =
                submissionMapper.findByProblemIdWithProblem(problemId, userId, pageParam);

        List<SubmissionListItemVO> voList = result.getRecords().stream()
                .map(submissionProjection::toListItemVO)
                .toList();

        return PageResult.of(voList, result.getTotal(), page, pageSize);
    }

    @Override
    public SubmissionVO findBest(Long problemId, String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_USER_ID_REQUIRED);
        }

        Optional<Submission> bestSubmission = submissionMapper.findBestByProblemIdAndUserId(problemId, userId);

        return bestSubmission.map(submissionProjection::toVO).orElse(null);
    }

    @Override
    public Optional<Submission> getSubmissionEntity(String id) {
        return Optional.ofNullable(submissionMapper.selectById(id));
    }

    @Override
    public void updateSubmissionResult(String submissionId, String status, int runtime,
                                        Double memory, List<Submission.TestCaseDetail> testDetails) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            log.warn("Cannot update result: submission {} not found", submissionId);
            return;
        }
        submission.setStatus(status);
        submission.setRuntime(runtime);
        submission.setMemory(memory);
        submission.setTestDetails(testDetails);
        if ("Accepted".equals(status)) {
            PerformanceStats stats = computePerformanceStats(submission, runtime, memory);
            applyPerformanceStatsToEntity(submission, stats);
        }
        submissionMapper.updateById(submission);
        log.info("Updated submission {} status={}, runtime={}ms, memory={}",
                submissionId, status, runtime, memory != null ? memory + "MB" : "N/A");

        // Trigger achievement checks for accepted submissions
        if ("Accepted".equals(status)) {
            // R6.3 / F-08: skip achievement triggers for virtual-contest
            // submissions. Virtual replays are not part of the user's
            // earned-achievements history.
            boolean isVirtual = contestParticipantMapper
                    .findIsVirtualBySubmissionId(submissionId)
                    .orElse(false);
            if (isVirtual) {
                log.info("R6.3 / F-08: skipping achievement triggers for virtual submission {}", submissionId);
            } else {
                try {
                    Long problemsSolved = submissionMapper.countAcceptedProblemsByUserId(submission.getUserId());
                    achievementTriggerService.onProblemSolved(submission.getUserId(), problemsSolved != null ? problemsSolved.intValue() : 0);
                    achievementTriggerService.onFirstProblemSolved(submission.getUserId());

                    // Language milestone
                    String language = submission.getLanguage();
                    if (language != null && !language.isBlank()) {
                        Long languageCount = submissionMapper.countByUserIdAndLanguage(submission.getUserId(), language);
                        achievementTriggerService.onLanguageMilestone(submission.getUserId(), language, languageCount != null ? languageCount.intValue() : 0);
                    }
                } catch (Exception e) {
                    log.warn("Failed to trigger achievements for submission {}: {}", submissionId, e.getMessage());
                }
            }
        }

        // Send submission result notification (fire-and-notify per D-11).
        // Q20: use the dispatch service. force=true because submission result
        // is system-originated — users opt out of category SYSTEM only via the
        // systemEnabled flag, which we still respect (no force for SYSTEM).
        // ADR-004 M4c: when useNotificationIntent flag is on, fan out via
        // the typed SubmissionCompletedIntent (InApp + Email + WebSocket,
        // failure-isolated). Otherwise the legacy path stays active.
        try {
            if (featureFlags.isUseNotificationIntent()) {
                Problem problem = problemMapper.selectById(submission.getProblemId());
                com.ulticode.modules.submission.enums.SubmissionStatus statusEnum =
                        com.ulticode.modules.submission.enums.SubmissionStatus.fromDbName(status);
                long elapsedMs = Math.max(0L, (long) runtime);
                long memBytes = memory == null ? 0L : (long) (memory * 1024 * 1024);
                notificationDispatcher.dispatch(
                        com.ulticode.modules.notification.intent.SubmissionCompletedIntent.of(
                                submission,
                                statusEnum != null
                                        ? statusEnum
                                        : com.ulticode.modules.submission.enums.SubmissionStatus.SYSTEM_ERROR,
                                problem != null ? problem.getTitle() : "",
                                elapsedMs,
                                memBytes,
                                null,
                                null));
            } else {
                notificationDispatchService.dispatch(
                        submission.getUserId(),
                        "SUBMISSION",
                        "SYSTEM",
                        "Submission judged: " + status,
                        "",
                        "/submissions/" + submission.getId(),
                        java.util.Map.of(
                                "submissionId", submission.getId(),
                                "problemId", submission.getProblemId(),
                                "problemTitle", problemMapper.selectById(submission.getProblemId()) != null
                                        ? problemMapper.selectById(submission.getProblemId()).getTitle()
                                        : "",
                                "status", status,
                                "isAccepted", "Accepted".equals(status)
                        ),
                        false);
            }
        } catch (Exception e) {
            log.warn("Failed to create submission notification for submission {}: {}",
                    submission.getId(), e.getMessage());
        }

        // P0-1: fire a SubmissionJudgedEvent so the contest scoring listener can
        // apply the verdict to contest_submissions + contest_participants aggregates.
        // Decoupled from the contest module so a scoring failure cannot break the
        // judge pipeline (the listener catches and logs its own exceptions).
        publishContestScoringEvent(submission, status);
    }

    /**
     * Publish a {@link SubmissionJudgedEvent} on the application event publisher.
     * Fire-and-forget: a publish failure is logged and swallowed, never
     * propagated to the caller (the verdict has already been written; we don't
     * want a publisher hiccup to surface as a 500 to the judge worker).
     */
    private void publishContestScoringEvent(Submission submission, String status) {
        if (applicationEventPublisher == null) {
            return;
        }
        try {
            SubmissionJudgedEvent event = new SubmissionJudgedEvent(
                    this,
                    submission.getId(),
                    submission.getUserId(),
                    submission.getProblemId(),
                    status,
                    "Accepted".equals(status),
                    submission.getRuntime(),
                    java.time.LocalDateTime.now()
            );
            applicationEventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("Failed to publish SubmissionJudgedEvent for submission {}: {}",
                    submission.getId(), e.getMessage());
        }
    }

    /**
     * ADR-003 M3b fenced verdict write. The worker calls this with the
     * {@code generation} and {@code attemptId} it observed at acquire time; the
     * underlying {@link SubmissionMapper#writeVerdictFencedWithStats} CAS rejects
     * the write if the generation has since been bumped (rejudge / reaper) or the
     * attempt lost its lease. On rejection the result is silently dropped and
     * the {@code judge.stale_result.dropped} counter increments.
     *
     * <p><b>F4 fix:</b> the computed performance stats (percentile + distribution
     * bins) are folded into the SAME generation+attempt CAS as the verdict, via
     * {@link SubmissionMapper#writeVerdictFencedWithStats}. The previous two-step
     * path (writeVerdictFenced CAS, then a separate unfenced
     * {@code submissionMapper.updateById} to persist the percentile columns)
     * had a window where an admin rejudge could bump the generation between the
     * CAS and the {@code updateById}; the unfenced update would then write the
     * stale Accepted status + old generation back over the rejudge, defeating
     * the fence exactly when {@code computePerformanceStats} is slow. Computing
     * the stats BEFORE the CAS and persisting them in the CAS eliminates the
     * second write entirely — all six data columns land (or are rejected)
     * atomically behind the fence.
     *
     * <p>The performance stats are computed only for the Accepted verdict; all
     * other verdicts pass nulls so the columns are cleared (matching the legacy
     * {@code updateSubmissionResult} behavior of always setting the field).
     *
     * <p>Achievements / notifications side-effects run only when the verdict
     * actually lands (affected = 1).
     *
     * @param submissionId  submission id
     * @param generation    generation observed at acquire (fence axis 1)
     * @param attemptId     attempt UUID held by the worker (fence axis 2)
     * @param status        terminal verdict wire value
     * @param runtime       runtime in ms
     * @param memory        memory in MB
     * @param testDetails   test case details (serialized to JSON)
     * @return {@code true} if the verdict was written; {@code false} if the
     *         fence rejected it (stale result dropped)
     */
    public boolean updateSubmissionResultFenced(String submissionId, long generation, String attemptId,
                                                String status, int runtime, Double memory,
                                                List<Submission.TestCaseDetail> testDetails) {
        // Serialize test details to JSON for the json column. Mirror the entity's
        // JacksonTypeHandler semantics by using the same ObjectMapper the service
        // already holds.
        String testDetailsJson = serializeTestDetails(testDetails);

        // F4: compute performance stats BEFORE the CAS and persist them in the
        // same fenced write. We read the row pre-CAS to get the problemId /
        // language / userId needed by computePerformanceStats (these columns are
        // not fenced — only status/runtime/memory/test_details/percentile/lease
        // are — so reading them now is safe even though the generation may move
        // between this read and the CAS). For non-Accepted verdicts we pass
        // nulls so the CAS clears the percentile columns, matching the legacy
        // updateSubmissionResult behavior of always overwriting the field.
        Double runtimePercentile = null;
        Double memoryPercentile = null;
        String runtimeDistBinsJson = null;
        String memoryDistBinsJson = null;
        if ("Accepted".equals(status)) {
            Submission pre = submissionMapper.selectById(submissionId);
            if (pre != null) {
                PerformanceStats stats = computePerformanceStats(pre, runtime, memory);
                runtimePercentile = stats.runtimePercentile();
                memoryPercentile = stats.memoryPercentile();
                runtimeDistBinsJson = serializeBins(stats.runtimeDistBinsMs());
                memoryDistBinsJson = serializeBins(stats.memoryDistBinsMb());
            }
        }

        int affected = submissionMapper.writeVerdictFencedWithStats(
                submissionId, generation, attemptId, status, runtime, memory, testDetailsJson,
                runtimePercentile, memoryPercentile, runtimeDistBinsJson, memoryDistBinsJson);

        if (affected == 0) {
            // Fence mismatch: the generation was bumped (rejudge / reaper) or
            // the attempt lost the lease. Drop the result and record the metric.
            incrementStaleResultDropped();
            log.debug("Stale judge result dropped for submission {} (gen={}, attempt={}, verdict={})",
                    submissionId, generation, attemptId, status);
            return false;
        }

        // Verdict landed. Re-read to get the canonical row for side-effects.
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            log.warn("Fenced verdict wrote but submission {} not found on re-read", submissionId);
            return true;
        }
        log.info("Updated submission {} (fenced) status={}, runtime={}ms, memory={}",
                submissionId, status, runtime, memory != null ? memory + "MB" : "N/A");

        // Achievements + notifications. F4: the fenced path no longer persists
        // performance stats here — they were written in the CAS above. The
        // side-effects (achievements / notifications) are not DB verdict writes,
        // so they are safe to run post-CAS without weakening the fence.
        triggerPostVerdictSideEffects(submission, status);
        return true;
    }

    /**
     * Serialize a distribution-bin list to a JSON string for the
     * {@code runtimeDistBinsMs} / {@code memoryDistBinsMb} json columns. Returns
     * {@code null} for an empty/null list so the CAS clears the column (matching
     * the legacy behavior of writing the field even when empty).
     */
    private String serializeBins(List<Map<String, Number>> bins) {
        if (bins == null || bins.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(bins);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to serialize distribution bins for fenced verdict: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Serialize test details to a JSON string for the {@code test_details} json
     * column. Returns {@code null} when the list is null/empty so the column is
     * cleared (matching legacy updateSubmissionResult which always sets the
     * field, but the fenced CAS writes NULL when null).
     */
    private String serializeTestDetails(List<Submission.TestCaseDetail> testDetails) {
        if (testDetails == null || testDetails.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(testDetails);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to serialize test details for fenced verdict: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Increment the {@code judge.stale_result.dropped} counter. No-op when no
     * meter registry is wired (e.g. integration tests).
     */
    private void incrementStaleResultDropped() {
        if (meterRegistry != null) {
            meterRegistry.counter("judge.stale_result.dropped").increment();
        }
    }

    /**
     * Run the achievement triggers and the submission-result notification for a
     * fenced verdict that just landed.
     *
     * <p><b>F4:</b> this method no longer persists performance stats. The fenced
     * path computes the stats BEFORE the verdict CAS and folds them into
     * {@link SubmissionMapper#writeVerdictFencedWithStats} so all six data
     * columns land atomically behind the generation+attempt fence. The previous
     * {@code computePerformanceStats} + {@code submissionMapper.updateById}
     * here was an <b>unfenced</b> write that could clobber a concurrent rejudge
     * bump happening between the verdict CAS and this side-effect step. The
     * legacy {@link #updateSubmissionResult} path keeps its own (unfenced, by
     * design — flag-off) {@code updateById} and does not call this method.
     */
    private void triggerPostVerdictSideEffects(Submission submission, String status) {
        // Achievements (no DB verdict write here — F4 moved performance stats
        // into the verdict CAS).
        if ("Accepted".equals(status)) {
            try {
                Long problemsSolved = submissionMapper.countAcceptedProblemsByUserId(submission.getUserId());
                achievementTriggerService.onProblemSolved(submission.getUserId(), problemsSolved != null ? problemsSolved.intValue() : 0);
                achievementTriggerService.onFirstProblemSolved(submission.getUserId());

                String language = submission.getLanguage();
                if (language != null && !language.isBlank()) {
                    Long languageCount = submissionMapper.countByUserIdAndLanguage(submission.getUserId(), language);
                    achievementTriggerService.onLanguageMilestone(submission.getUserId(), language, languageCount != null ? languageCount.intValue() : 0);
                }
            } catch (Exception e) {
                log.warn("Failed to trigger achievements for submission {}: {}", submission.getId(), e.getMessage());
            }
        }

        // Notification
        // ADR-004 M4c: route through the typed dispatcher when the flag is on.
        try {
            if (featureFlags.isUseNotificationIntent()) {
                Problem problem = problemMapper.selectById(submission.getProblemId());
                com.ulticode.modules.submission.enums.SubmissionStatus statusEnum =
                        com.ulticode.modules.submission.enums.SubmissionStatus.fromDbName(status);
                Integer runtimeVal = submission.getRuntime();
                Double memMb = submission.getMemory();
                long elapsedMs = runtimeVal == null ? 0L : Math.max(0L, runtimeVal.longValue());
                long memBytes = memMb == null ? 0L : (long) (memMb * 1024 * 1024);
                notificationDispatcher.dispatch(
                        com.ulticode.modules.notification.intent.SubmissionCompletedIntent.of(
                                submission,
                                statusEnum != null
                                        ? statusEnum
                                        : com.ulticode.modules.submission.enums.SubmissionStatus.SYSTEM_ERROR,
                                problem != null ? problem.getTitle() : "",
                                elapsedMs,
                                memBytes,
                                null,
                                null));
            } else {
                Problem problem = problemMapper.selectById(submission.getProblemId());
                notificationDispatchService.dispatch(
                        submission.getUserId(),
                        "SUBMISSION",
                        "SYSTEM",
                        "Submission judged: " + status,
                        "",
                        "/submissions/" + submission.getId(),
                        java.util.Map.of(
                                "submissionId", submission.getId(),
                                "problemId", submission.getProblemId(),
                                "problemTitle", problem != null ? problem.getTitle() : "",
                                "status", status,
                                "isAccepted", "Accepted".equals(status)
                        ),
                        false);
            }
        } catch (Exception e) {
            log.warn("Failed to create submission notification for submission {}: {}",
                    submission.getId(), e.getMessage());
        }
    }

    private PerformanceStats computePerformanceStats(Submission current, int runtime, Double memory) {
        // Per-user best stats aggregated in SQL. Bounded by distinct-user
        // count, not by total accepted submissions (see SubmissionMapper
        // #findBestStatsByProblemAndLanguage).
        List<UserBestStats> peerBest = submissionMapper.findBestStatsByProblemAndLanguage(
                current.getProblemId(), current.getLanguage());
        if (peerBest == null) {
            peerBest = List.of();
        }

        List<Double> peerRuntimes = new ArrayList<>();
        List<Double> peerMemories = new ArrayList<>();
        for (UserBestStats stats : peerBest) {
            // Skip the current user — "better than X% of OTHER users" is
            // the intended comparison axis, matching the previous in-memory
            // implementation.
            if (Objects.equals(stats.userId(), current.getUserId())) {
                continue;
            }
            if (stats.bestRuntimeMs() != null && stats.bestRuntimeMs() >= 0) {
                peerRuntimes.add(stats.bestRuntimeMs().doubleValue());
            }
            if (stats.bestMemoryMb() != null && stats.bestMemoryMb() >= 0) {
                peerMemories.add(stats.bestMemoryMb());
            }
        }

        Double runtimePercentile = null;
        List<Map<String, Number>> runtimeBins = List.of();
        if (runtime >= 0) {
            List<Double> runtimes = new ArrayList<>(peerRuntimes);
            runtimes.add((double) runtime);
            runtimePercentile = calculateBetterThanPercentile(runtimes, runtime);
            runtimeBins = buildDistributionBins(runtimes);
        }

        Double memoryPercentile = null;
        List<Map<String, Number>> memoryBins = List.of();
        if (memory != null && memory >= 0) {
            List<Double> memories = new ArrayList<>(peerMemories);
            memories.add(memory);
            memoryPercentile = calculateBetterThanPercentile(memories, memory);
            memoryBins = buildDistributionBins(memories);
        }

        return new PerformanceStats(runtimePercentile, runtimeBins, memoryPercentile, memoryBins);
    }

    /**
     * Apply a {@link PerformanceStats} snapshot to the entity so that the
     * next {@code submissionMapper.updateById} persists the percentile and
     * distribution bin fields. Used by the write path
     * ({@link #updateSubmissionResult}); the read path
     * ({@link #findById}) instead threads the stats into the VO without
     * touching the entity, so this method is intentionally not called from
     * there.
     */
    private void applyPerformanceStatsToEntity(Submission entity, PerformanceStats stats) {
        if (stats == null) {
            return;
        }
        entity.setRuntimePercentile(stats.runtimePercentile());
        entity.setRuntimeDistBinsMs(stats.runtimeDistBinsMs());
        entity.setMemoryPercentile(stats.memoryPercentile());
        entity.setMemoryDistBinsMb(stats.memoryDistBinsMb());
    }

    private double calculateBetterThanPercentile(List<Double> values, double currentValue) {
        if (values.isEmpty()) {
            return 0.0;
        }
        long slowerCount = values.stream()
                .filter(value -> value > currentValue)
                .count();
        return Math.round((slowerCount * 1000.0) / values.size()) / 10.0;
    }

    private List<Map<String, Number>> buildDistributionBins(List<Double> values) {
        if (values.isEmpty()) {
            return List.of();
        }

        Map<Double, Long> exactCounts = values.stream()
                .filter(Objects::nonNull)
                .sorted()
                .collect(
                        LinkedHashMap::new,
                        (counts, value) -> counts.merge(value, 1L, Long::sum),
                        LinkedHashMap::putAll);

        if (exactCounts.size() <= DEFAULT_DISTRIBUTION_BIN_COUNT) {
            return exactCounts.entrySet().stream()
                    .map(entry -> Map.<String, Number>of(
                            "bin", formatDistributionBin(entry.getKey()),
                            "count", entry.getValue()))
                    .toList();
        }

        double min = values.stream().min(Comparator.naturalOrder()).orElse(0.0);
        double max = values.stream().max(Comparator.naturalOrder()).orElse(min);
        if (Double.compare(min, max) == 0) {
            return List.of(Map.<String, Number>of(
                    "bin", formatDistributionBin(min),
                    "count", values.size()));
        }

        int bucketCount = DEFAULT_DISTRIBUTION_BIN_COUNT;
        double bucketSize = (max - min) / bucketCount;
        long[] counts = new long[bucketCount];
        for (Double value : values) {
            int index = (int) Math.floor((value - min) / bucketSize);
            counts[Math.min(index, bucketCount - 1)]++;
        }

        List<Map<String, Number>> bins = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) {
            bins.add(Map.<String, Number>of(
                    "bin", formatDistributionBin(min + (bucketSize * i)),
                    "count", counts[i]));
        }
        return bins;
    }

    /**
     * Round a bin label to one decimal place. Always returns a
     * {@code double} so JSON consumers (frontend) see a single stable type
     * for the {@code bin} field regardless of whether the underlying
     * value happens to be an integer.
     */
    private double formatDistributionBin(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    @Override
    public List<SubmissionStatusMeta> getStatuses() {
        List<SubmissionStatusMeta> statuses = new ArrayList<>();

        // Pending
        SubmissionStatusMeta pending = new SubmissionStatusMeta();
        pending.setKey("Pending");
        pending.setCode("PENDING");
        pending.setLabel("Pending");
        pending.setDescription("Submission is waiting to be judged");
        pending.setSuggestion("Please wait for the judging to complete");
        pending.setCategory("pending");
        pending.setSeverity("info");
        pending.setIsTerminal(false);
        pending.setSortOrder(0);
        statuses.add(pending);

        // Judging
        SubmissionStatusMeta judging = new SubmissionStatusMeta();
        judging.setKey("Judging");
        judging.setCode("JUDGING");
        judging.setLabel("Judging");
        judging.setDescription("Submission is being judged");
        judging.setSuggestion("Please wait for the judging to complete");
        judging.setCategory("pending");
        judging.setSeverity("info");
        judging.setIsTerminal(false);
        judging.setSortOrder(1);
        statuses.add(judging);

        // Accepted
        SubmissionStatusMeta accepted = new SubmissionStatusMeta();
        accepted.setKey("Accepted");
        accepted.setCode("ACCEPTED");
        accepted.setLabel("Accepted");
        accepted.setDescription("All test cases passed");
        accepted.setSuggestion("Congratulations! Your solution is correct.");
        accepted.setCategory("success");
        accepted.setSeverity("success");
        accepted.setIsTerminal(true);
        accepted.setSortOrder(2);
        statuses.add(accepted);

        // Wrong Answer
        SubmissionStatusMeta wrongAnswer = new SubmissionStatusMeta();
        wrongAnswer.setKey("Wrong Answer");
        wrongAnswer.setCode("WRONG_ANSWER");
        wrongAnswer.setLabel("Wrong Answer");
        wrongAnswer.setDescription("Your output was incorrect");
        wrongAnswer.setSuggestion("Check your algorithm and edge cases");
        wrongAnswer.setCategory("error");
        wrongAnswer.setSeverity("error");
        wrongAnswer.setIsTerminal(true);
        wrongAnswer.setSortOrder(3);
        statuses.add(wrongAnswer);

        // Time Limit Exceeded
        SubmissionStatusMeta tle = new SubmissionStatusMeta();
        tle.setKey("Time Limit Exceeded");
        tle.setCode("TIME_LIMIT_EXCEEDED");
        tle.setLabel("Time Limit Exceeded");
        tle.setDescription("Your program took too long to execute");
        tle.setSuggestion("Optimize your algorithm or reduce unnecessary operations");
        tle.setCategory("error");
        tle.setSeverity("error");
        tle.setIsTerminal(true);
        tle.setSortOrder(4);
        statuses.add(tle);

        // Memory Limit Exceeded
        SubmissionStatusMeta mle = new SubmissionStatusMeta();
        mle.setKey("Memory Limit Exceeded");
        mle.setCode("MEMORY_LIMIT_EXCEEDED");
        mle.setLabel("Memory Limit Exceeded");
        mle.setDescription("Your program used too much memory");
        mle.setSuggestion("Optimize memory usage or use more efficient data structures");
        mle.setCategory("error");
        mle.setSeverity("error");
        mle.setIsTerminal(true);
        mle.setSortOrder(5);
        statuses.add(mle);

        // Output Limit Exceeded
        SubmissionStatusMeta ole = new SubmissionStatusMeta();
        ole.setKey("Output Limit Exceeded");
        ole.setCode("OUTPUT_LIMIT_EXCEEDED");
        ole.setLabel("Output Limit Exceeded");
        ole.setDescription("Your program produced too much output");
        ole.setSuggestion("Check for infinite loops that produce output");
        ole.setCategory("error");
        ole.setSeverity("error");
        ole.setIsTerminal(true);
        ole.setSortOrder(6);
        statuses.add(ole);

        // Runtime Error
        SubmissionStatusMeta runtimeError = new SubmissionStatusMeta();
        runtimeError.setKey("Runtime Error");
        runtimeError.setCode("RUNTIME_ERROR");
        runtimeError.setLabel("Runtime Error");
        runtimeError.setDescription("Your program crashed during execution");
        runtimeError.setSuggestion("Check for division by zero, null pointer, array out of bounds, etc.");
        runtimeError.setCategory("error");
        runtimeError.setSeverity("error");
        runtimeError.setIsTerminal(true);
        runtimeError.setSortOrder(7);
        statuses.add(runtimeError);

        // Compile Error
        SubmissionStatusMeta compileError = new SubmissionStatusMeta();
        compileError.setKey("Compile Error");
        compileError.setCode("COMPILE_ERROR");
        compileError.setLabel("Compile Error");
        compileError.setDescription("Your code failed to compile");
        compileError.setSuggestion("Check syntax errors and make sure your code is valid");
        compileError.setCategory("error");
        compileError.setSeverity("error");
        compileError.setIsTerminal(true);
        compileError.setSortOrder(8);
        statuses.add(compileError);

        // Presentation Error
        SubmissionStatusMeta presentationError = new SubmissionStatusMeta();
        presentationError.setKey("Presentation Error");
        presentationError.setCode("PRESENTATION_ERROR");
        presentationError.setLabel("Presentation Error");
        presentationError.setDescription("Your output format is incorrect");
        presentationError.setSuggestion("Check for extra spaces, newlines, or formatting issues");
        presentationError.setCategory("error");
        presentationError.setSeverity("warning");
        presentationError.setIsTerminal(true);
        presentationError.setSortOrder(9);
        statuses.add(presentationError);

        // System Error
        SubmissionStatusMeta systemError = new SubmissionStatusMeta();
        systemError.setKey("System Error");
        systemError.setCode("SYSTEM_ERROR");
        systemError.setLabel("System Error");
        systemError.setDescription("An error occurred on our end");
        systemError.setSuggestion("Please try again later or contact support");
        systemError.setCategory("system");
        systemError.setSeverity("error");
        systemError.setIsTerminal(true);
        systemError.setSortOrder(10);
        statuses.add(systemError);

        return statuses;
    }

    /**
     * Record contest submission if user is participating in an active contest containing this problem.
     * Per D-04: creates ContestSubmission alongside regular Submission in same transaction.
     * Per D-06: only records if user has STARTED status (matches DB enum).
     */
    private void recordContestSubmissionIfNeeded(String submissionId, String userId, Long problemId) {
        // 1. Find contest_problems containing this problem
        List<ContestProblem> contestProblems = contestProblemMapper.findByProblemId(problemId);

        for (ContestProblem cp : contestProblems) {
            // 2. Check if contest is RUNNING
            Contest contest = contestMapper.selectById(cp.getContestId());
            if (contest == null || !ContestStatus.RUNNING.name().equals(contest.getStatus())) {
                continue;
            }

            // 3. Check if user has STARTED status (D-06 -- matches DB enum 'STARTED')
            Optional<ContestParticipant> participant = contestParticipantMapper
                    .findByContestIdAndUserId(cp.getContestId(), userId);
            if (participant.isEmpty() ||
                    !ContestParticipantStatus.STARTED.name().equals(participant.get().getStatus())) {
                continue;
            }

            // 4. Create ContestSubmission (D-05)
            ContestSubmission cs = new ContestSubmission();
            cs.setSubmissionId(submissionId);
            cs.setContestId(cp.getContestId());
            cs.setContestProblemId(cp.getId());
            cs.setParticipantId(participant.get().getId());
            // R6.2 / F-06: pick the right clock per participant type. Real
            // contests use actualStartTime (fallback to startTime if admin
            // never triggered the scheduler transition); virtual sessions
            // use the participant's own startedAt — contest.startTime is
            // irrelevant for replays and would yield nonsense offsets.
            ContestParticipant p = participant.get();
            LocalDateTime contestClock = contest.getActualStartTime() != null
                    ? contest.getActualStartTime()
                    : contest.getStartTime();
            LocalDateTime virtualClock = p.getStartedAt();
            LocalDateTime clock = Boolean.TRUE.equals(p.getIsVirtual()) ? virtualClock : contestClock;
            if (clock != null) {
                cs.setTimeFromStart((int) Duration.between(clock, LocalDateTime.now()).getSeconds());
            }
            cs.setIsAccepted(false); // Will be updated when judge completes
            cs.setSubmittedAt(LocalDateTime.now());
            contestSubmissionMapper.insert(cs);
            realtimeService.markDirty(contest.getId());

            // Only record for the first matching active contest
            break;
        }
    }
}
