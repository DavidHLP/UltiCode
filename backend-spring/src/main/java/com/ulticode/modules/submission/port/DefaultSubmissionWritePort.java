package com.ulticode.modules.submission.port;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.achievement.constants.AchievementType;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.queue.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.queue.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.dispatcher.JudgedNotificationDispatcher;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.PerformanceStats;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.event.SubmissionJudgedEvent;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.stats.SubmissionPerformanceStats;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Default (and only) adapter for {@link SubmissionWritePort}. Owns the
 * Submission intake + the two verdict writers — see the interface javadoc for
 * why this is a deep module.
 *
 * <p>Logic moved verbatim from the deprecated {@code SubmissionServiceImpl}
 * facade. The facade stays as a thin delegate so cross-module callers
 * ({@code ContestServiceImpl#submit}, {@code JudgeWorkerProcessor} verdict
 * writes) see zero behavioural change. Every guard the facade used to inline
 * is preserved here: {@code @Transactional} on intake, ADR-003 §5
 * {@code judge.stale_result.dropped} counter, the F4 same-CAS stats write,
 * and fire-and-forget isolation of contest-recording / achievement /
 * notification failures.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultSubmissionWritePort implements SubmissionWritePort {

    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ProblemFactsPort problemFacts;
    private final ObjectMapper objectMapper;
    private final SubmissionProjection submissionProjection;
    private final SubmissionPerformanceStats performanceStats;
    private final QueueService queueService;
    private final ContestSubmissionPort contestSubmissionPort;
    private final AchievementTriggerService achievementTriggerService;
    /**
     * Deep module owning the post-verdict notification dispatch (the legacy +
     * fenced paths that used to be 95% duplicated inside this port).
     * Owns its own collaborators (notification dispatcher / dispatch service /
     * problem mapper + flag check) so this port's constructor drops three
     * notification collaborators.
     */
    private final JudgedNotificationDispatcher judgedNotificationDispatcher;
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
    private final MeterRegistry meterRegistry;
    /**
     * Publishes {@link SubmissionJudgedEvent} after the verdict-write
     * transaction commits. Consumed by
     * {@code com.ulticode.modules.contest.listener.ContestScoringListener}
     * (a {@code @TransactionalEventListener(AFTER_COMMIT)}) to apply contest
     * scoring (P0-1). The publish is fire-and-forget; an exception here is
     * logged but never propagated to the judge worker.
     */
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    /**
     * Supported languages for submission.
     */
    private static final List<String> SUPPORTED_LANGUAGES = List.of(
            "javascript", "python", "java", "c", "cpp"
    );

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
        if (problemFacts.findDisplayFacts(createDTO.getProblemId()) == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }

        // Verify user exists
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Create submission with Pending status
        Submission submission = new Submission();
        submission.setId(uuidGenerator.newId());
        submission.setUserId(userId);
        submission.setProblemId(createDTO.getProblemId());
        submission.setLanguage(language);
        submission.setCode(createDTO.getCode());
        submission.setStatus("Pending");
        submission.setRuntime(0);
        submission.setMemory(0.0);
        submission.setCreatedAt(LocalDateTime.now(clock));
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
                    submission, String.valueOf(createDTO.getProblemId()), generation, isShadow,
                    uuidGenerator));
        }

        // --- Contest submission recording (D-04, D-05, D-06) ---
        try {
            contestSubmissionPort.recordSubmissionIfNeeded(submission.getId(), userId, createDTO.getProblemId());
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
            PerformanceStats stats = performanceStats.compute(submission, runtime, memory);
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
            boolean isVirtual = contestSubmissionPort.isVirtualParticipation(submissionId);
            if (isVirtual) {
                log.info("R6.3 / F-08: skipping achievement triggers for virtual submission {}", submissionId);
            } else {
                triggerAchievements(submission);
            }
        }

        // Send submission result notification (fire-and-notify per D-11).
        // Q20: use the dispatch service. force=true because submission result
        // is system-originated — users opt out of category SYSTEM only via the
        // systemEnabled flag, which we still respect (no force for SYSTEM).
        // ADR-004 M4c: when useNotificationIntent flag is on, fan out via
        // the typed SubmissionCompletedIntent (InApp + Email + WebSocket,
        // failure-isolated). Otherwise the legacy path stays active.
        // Both converge on JudgedNotificationDispatcher — single source of truth.
        judgedNotificationDispatcher.dispatch(submission, status, runtime, memory);

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
                    java.time.LocalDateTime.now(clock)
            );
            applicationEventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("Failed to publish SubmissionJudgedEvent for submission {}: {}",
                    submission.getId(), e.getMessage());
        }
    }

    /**
     * ADR-003 M3b fenced verdict write. The worker calls this with the
     * {@code generation} and {@code attemptId} it observed at acquire time;
     * the underlying {@link SubmissionMapper#writeVerdictFencedWithStats} CAS
     * rejects the write if the generation has since been bumped (rejudge /
     * reaper) or the attempt lost its lease. On rejection the result is
     * silently dropped and the {@code judge.stale_result.dropped} counter
     * increments.
     *
     * <p><b>F4 fix:</b> the computed performance stats (percentile +
     * distribution bins) are folded into the SAME generation+attempt CAS as
     * the verdict, via {@link SubmissionMapper#writeVerdictFencedWithStats}.
     * The previous two-step path (writeVerdictFenced CAS, then a separate
     * unfenced {@code submissionMapper.updateById} to persist the percentile
     * columns) had a window where an admin rejudge could bump the generation
     * between the CAS and the {@code updateById}; the unfenced update would
     * then write the stale Accepted status + old generation back over the
     * rejudge, defeating the fence exactly when
     * {@code performanceStats.compute} is slow. Computing the stats BEFORE
     * the CAS and persisting them in the CAS eliminates the second write
     * entirely — all six data columns land (or are rejected) atomically
     * behind the fence.
     *
     * <p>The performance stats are computed only for the Accepted verdict;
     * all other verdicts pass nulls so the columns are cleared (matching the
     * legacy {@code updateSubmissionResult} behavior of always setting the
     * field).
     *
     * <p>Achievements / notifications side-effects run only when the verdict
     * actually lands (affected = 1).
     */
    @Override
    public boolean updateSubmissionResultFenced(String submissionId, long generation, String attemptId,
                                                String status, int runtime, Double memory,
                                                List<Submission.TestCaseDetail> testDetails) {
        // Serialize test details to JSON for the json column. Mirror the entity's
        // JacksonTypeHandler semantics by using the same ObjectMapper the service
        // already holds.
        String testDetailsJson = serializeTestDetails(testDetails);

        // F4: compute performance stats BEFORE the CAS and persist them in the
        // same fenced write. We read the row pre-CAS to get the problemId /
        // language / userId needed by performanceStats.compute (these columns
        // are not fenced — only status/runtime/memory/test_details/percentile
        // /lease are — so reading them now is safe even though the generation
        // may move between this read and the CAS). For non-Accepted verdicts
        // we pass nulls so the CAS clears the percentile columns, matching
        // the legacy updateSubmissionResult behavior of always overwriting
        // the field.
        Double runtimePercentile = null;
        Double memoryPercentile = null;
        String runtimeDistBinsJson = null;
        String memoryDistBinsJson = null;
        if ("Accepted".equals(status)) {
            Submission pre = submissionMapper.selectById(submissionId);
            if (pre != null) {
                PerformanceStats stats = performanceStats.compute(pre, runtime, memory);
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
        // side-effects (achievements / notifications) are not DB verdict
        // writes, so they are safe to run post-CAS without weakening the
        // fence.
        triggerPostVerdictSideEffects(submission, status);
        return true;
    }

    /**
     * Serialize a distribution-bin list to a JSON string for the
     * {@code runtimeDistBinsMs} / {@code memoryDistBinsMb} json columns.
     * Returns {@code null} for an empty/null list so the CAS clears the column
     * (matching the legacy behavior of writing the field even when empty).
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
     * Serialize test details to a JSON string for the {@code test_details}
     * json column. Returns {@code null} when the list is null/empty so the
     * column is cleared (matching legacy updateSubmissionResult which always
     * sets the field, but the fenced CAS writes NULL when null).
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
     * Run the achievement triggers for an Accepted verdict. Centralised so
     * every Accepted verdict shares one implementation; the caller decides
     * whether the virtual-contest guard applies (the unfenced flag-off path
     * runs the guard, the fenced path historically does not — preserved
     * verbatim, see the interface javadoc).
     */
    private void triggerAchievements(Submission submission) {
        try {
            Long problemsSolved = submissionMapper.countAcceptedProblemsByUserId(submission.getUserId());
            achievementTriggerService.trigger(submission.getUserId(),
                    AchievementType.PROBLEMS_SOLVED,
                    problemsSolved != null ? problemsSolved.intValue() : 0);
            achievementTriggerService.trigger(submission.getUserId(),
                    AchievementType.FIRST_PROBLEM, 1);

            // Language milestone
            String language = submission.getLanguage();
            if (language != null && !language.isBlank()) {
                Long languageCount = submissionMapper.countByUserIdAndLanguage(submission.getUserId(), language);
                achievementTriggerService.trigger(submission.getUserId(),
                        AchievementType.LANGUAGE_SOLVED,
                        languageCount != null ? languageCount.intValue() : 0);
            }
        } catch (Exception e) {
            log.warn("Failed to trigger achievements for submission {}: {}", submission.getId(), e.getMessage());
        }
    }

    /**
     * Run the achievement triggers and the submission-result notification for
     * a fenced verdict that just landed.
     *
     * <p><b>F4:</b> this method no longer persists performance stats. The
     * fenced path computes the stats BEFORE the verdict CAS and folds them
     * into {@link SubmissionMapper#writeVerdictFencedWithStats} so all six
     * data columns land atomically behind the generation+attempt fence. The
     * previous {@code performanceStats.compute} +
     * {@code submissionMapper.updateById} here was an <b>unfenced</b> write
     * that could clobber a concurrent rejudge bump happening between the
     * verdict CAS and this side-effect step. The legacy
     * {@link #updateSubmissionResult} path keeps its own (unfenced, by
     * design — flag-off) {@code updateById} and does not call this method.
     */
    private void triggerPostVerdictSideEffects(Submission submission, String status) {
        // Achievements (no DB verdict write here — F4 moved performance stats
        // into the verdict CAS). NOTE: the fenced path does not run the
        // virtual-contest guard that the unfenced path runs — preserved
        // verbatim from the facade; flag-day cleanup is tracked separately.
        if ("Accepted".equals(status)) {
            triggerAchievements(submission);
        }

        // Notification — single seam: JudgedNotificationDispatcher. Both the
        // legacy and fenced paths converge on it; values come from the
        // canonical row re-read after the CAS landed.
        Integer runtimeVal = submission.getRuntime();
        Double memMb = submission.getMemory();
        long elapsed = runtimeVal == null ? 0L : runtimeVal.longValue();
        judgedNotificationDispatcher.dispatch(submission, status, elapsed, memMb);
    }

    /**
     * Apply a {@link PerformanceStats} snapshot to the entity so that the
     * next {@code submissionMapper.updateById} persists the percentile and
     * distribution bin fields. Used by the unfenced write path
     * ({@link #updateSubmissionResult}); the read path threads the stats
     * into the VO without touching the entity, so this method is
     * intentionally not called from there.
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
}
