package com.ulticode.modules.submission.port;

import com.ulticode.app.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.PerformanceStats;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.app.api.event.SubmissionJudgedEvent;
import com.ulticode.app.api.service.ContestSubmissionPort;
import com.ulticode.app.api.service.JudgeEnqueuePort;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.SubmissionWritePort;
import com.ulticode.app.api.service.UserExistencePort;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.codec.SubmissionStatusCodec;
import com.ulticode.modules.submission.codec.TestCaseDetailCodec;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.modules.submission.result.SubmissionResultOutboxWriter;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.stats.SubmissionPerformanceStats;
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
 * Submission intake + the two verdict writers.
 *
 * <p>All cross-module dependencies are injected as ports declared in
 * {@code com.ulticode.app.api.service}. The production adapter wires:
 * <ul>
 *   <li>{@code JudgeEnqueuePort} → delegates to {@code QueueService}</li>
 *   <li>{@code UserExistencePort} → delegates to {@code UserMapper}</li>
 * </ul>
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultSubmissionWritePort implements SubmissionWritePort {

    private final SubmissionMapper submissionMapper;
    private final ProblemFactsPort problemFacts;
    private final UserExistencePort userExistencePort;
    private final ObjectMapper objectMapper;
    private final SubmissionProjection submissionProjection;
    private final SubmissionPerformanceStats performanceStats;
    private final JudgeEnqueuePort judgeEnqueuePort;
    private final ContestSubmissionPort contestSubmissionPort;
    private final JudgeOutboxMapper judgeOutboxMapper;
    private final FeatureFlagsProperties featureFlags;
    private final MeterRegistry meterRegistry;
    private final SubmissionResultOutboxWriter resultOutboxWriter;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    private static final List<String> SUPPORTED_LANGUAGES = List.of(
            "javascript", "python", "java", "c", "cpp"
    );

    @Override
    @Transactional
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST);
        }
        if (!StringUtils.hasText(createDTO.getCode())) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST);
        }
        String language = createDTO.getLanguage().toLowerCase();
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST);
        }
        if (problemFacts.findDisplayFacts(createDTO.getProblemId()) == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND);
        }
        if (!userExistencePort.existsById(userId)) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND);
        }

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

        submissionMapper.insert(submission);
        log.info("Created submission {} for user {} and problem {}",
                submission.getId(), userId, createDTO.getProblemId());

        boolean portActive = featureFlags.getJudgeQueue().isUsePort();
        if (featureFlags.isUseJudgeOutbox() && judgeOutboxMapper != null) {
            long generation = submission.getGeneration() != null ? submission.getGeneration() : 1L;
            boolean isShadow = !portActive;
            judgeOutboxMapper.insert(JudgeOutboxRecord.of(
                    submission, String.valueOf(createDTO.getProblemId()),
                    generation, isShadow, uuidGenerator));
        }

        try {
            contestSubmissionPort.recordSubmissionIfNeeded(
                    submission.getId(), userId, createDTO.getProblemId());
        } catch (Exception e) {
            log.warn("Failed to record contest submission for submission {}: {}",
                    submission.getId(), e.getMessage());
        }

        if (portActive) {
            log.debug("Submit {} skipped legacy RQueue (port cutover active)", submission.getId());
        } else {
            try {
                judgeEnqueuePort.enqueueJudgeJob(
                        submission.getId(),
                        String.valueOf(createDTO.getProblemId()),
                        userId,
                        language,
                        createDTO.getCode());
                log.info("Enqueued judge job for submission {}", submission.getId());
            } catch (Exception e) {
                log.error("Failed to enqueue judge job for submission {}", submission.getId(), e);
                submission.setStatus(SubmissionStatus.SYSTEM_ERROR.wireValue());
                submission.setNotes("Judge queue unavailable — submission was not processed");
                submissionMapper.updateById(submission);
            }
        }

        return submissionProjection.toVO(submission);
    }

    @Override
    @Transactional
    public void updateSubmissionResult(String submissionId, SubmissionStatus status, int runtime,
                                       Double memory, String testDetailsJson) {
        String wire = SubmissionStatusCodec.toWire(status);
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            log.warn("Cannot update result: submission {} not found", submissionId);
            return;
        }
        submission.setStatus(wire);
        submission.setRuntime(runtime);
        submission.setMemory(memory);
        submission.setTestDetails(TestCaseDetailCodec.fromJson(testDetailsJson));
        if (status == SubmissionStatus.ACCEPTED) {
            PerformanceStats stats = performanceStats.compute(submission, runtime, memory);
            applyPerformanceStatsToEntity(submission, stats);
        }
        submissionMapper.updateById(submission);
        log.info("Updated submission {} status={}, runtime={}ms, memory={}",
                submissionId, wire, runtime, memory != null ? memory + "MB" : "N/A");

        publishContestScoringEvent(submission, status,
                submission.getGeneration() != null ? submission.getGeneration() : 1L,
                runtime, memory != null ? memory : 0,
                contestSubmissionPort.findContestId(submissionId));

    }

    @Override
    @Transactional
    public boolean updateSubmissionResultFenced(String submissionId, SubmissionStatus status,
                                                int runtime, Double memory, String testDetailsJson,
                                                long generation, String attemptId) {
        String wire = SubmissionStatusCodec.toWire(status);

        Double runtimePercentile = null;
        Double memoryPercentile = null;
        String runtimeDistBinsJson = null;
        String memoryDistBinsJson = null;
        if (status == SubmissionStatus.ACCEPTED) {
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
                submissionId, generation, attemptId, wire, runtime, memory, testDetailsJson,
                runtimePercentile, memoryPercentile, runtimeDistBinsJson, memoryDistBinsJson);

        if (affected == 0) {
            incrementStaleResultDropped();
            log.debug("Stale judge result dropped for submission {} (gen={}, attempt={}, verdict={})",
                    submissionId, generation, attemptId, wire);
            return false;
        }

        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            log.warn("Fenced verdict wrote but submission {} not found on re-read", submissionId);
            return true;
        }
        publishContestScoringEvent(submission, status,
                generation, runtime, memory != null ? memory : 0,
                contestSubmissionPort.findContestId(submissionId));
        return true;
    }

    private void publishContestScoringEvent(Submission submission, SubmissionStatus status,
                                            long generation, int runtimeMs, double memoryMb,
                                            String contestId) {
        try {
            if (applicationEventPublisher == null) {
                throw new IllegalStateException("ApplicationEventPublisher unavailable");
            }
            SubmissionJudgedEvent event = new SubmissionJudgedEvent(
                    this,
                    submission.getId(),
                    submission.getUserId(),
                    submission.getProblemId(),
                    SubmissionStatusCodec.toWire(status),
                    status == SubmissionStatus.ACCEPTED,
                    submission.getRuntime(),
                    LocalDateTime.now(clock),
                    generation,
                    runtimeMs,
                    memoryMb,
                    contestId
            );
            applicationEventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("Failed to publish SubmissionJudgedEvent for submission {}: {}",
                    submission.getId(), e.getMessage());
            if (status.isTerminal()) {
                resultOutboxWriter.recordVerdictResult(
                        submission.getId(),
                        generation > 0 ? generation : 1L,
                        submission.getUserId(),
                        String.valueOf(submission.getProblemId()),
                        SubmissionStatusCodec.toWire(status),
                        runtimeMs,
                        memoryMb,
                        contestId);
            }
        }
    }

    private String serializeBins(List<Map<String, Number>> bins) {
        if (bins == null || bins.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(bins);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to serialize distribution bins: {}", e.getMessage());
            return null;
        }
    }

    private void incrementStaleResultDropped() {
        if (meterRegistry != null) {
            meterRegistry.counter("judge.stale_result.dropped").increment();
        }
    }

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
