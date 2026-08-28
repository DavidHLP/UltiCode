package com.ulticode.modules.submission.port;

import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.PerformanceStats;
import com.ulticode.submission.api.dto.SubmissionTestCaseDetailDTO;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.submission.api.codec.TestCaseDetailCodec;
import com.ulticode.submission.api.event.SubmissionJudgedEvent;
import com.ulticode.app.api.service.ContestSubmissionPort;
import com.ulticode.submission.api.service.SubmissionIntakePort;
import com.ulticode.submission.api.service.SubmissionVerdictWritePort;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.codec.SubmissionStatusCodec;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.modules.submission.result.SubmissionResultOutboxWriter;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxWriter;
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
 * Local storage-writer adapter for {@link SubmissionIntakePort} and
 * {@link SubmissionVerdictWritePort}, owned by
 * {@code backend-submission}.
 *
 * <p>SPLIT-003 local owner writer. It writes the {@code submission} schema
 * tables ({@code submissions}, {@code judge_outbox},
 * {@code submission_result_outbox}, and contest-intake
 * {@code submission_created_outbox}) inside local transactions.
 *
 * <p>Contract notes:
 * <ul>
 *   <li><b>Contest submissions use the explicit command.</b> Generic
 *       {@link #submit} rejects contest context; {@link #submitContest}
 *       writes a durable association event for the App Contest owner.</li>
 *   <li><b>Judge dispatch.</b> In the outbox-active mode
 *       ({@code useJudgeOutbox && usePort}) the outbox row is the sole
 *       producer; legacy RQueue enqueue is skipped exactly like the App
 *       writer. In legacy mode this adapter logs and does not enqueue — the
 *       caller must keep {@code useJudgeOutbox=true} while this owner runs.</li>
 *   <li><b>Events.</b> {@code SubmissionJudgedEvent} is published on the local
 *       {@code ApplicationEventPublisher}; cross-service consumers arrive with
 *       the result-outbox dispatcher in a later slice. Terminal verdicts are
 *       always durably recorded in {@code submission_result_outbox}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultSubmissionWritePort implements SubmissionIntakePort, SubmissionVerdictWritePort {

    private final SubmissionMapper submissionMapper;
    private final ObjectMapper objectMapper;
    private final SubmissionProjection submissionProjection;
    private final SubmissionPerformanceStats performanceStats;
    private final ContestSubmissionPort contestSubmissionPort;
    private final JudgeOutboxMapper judgeOutboxMapper;
    private final FeatureFlagsProperties featureFlags;
    private final MeterRegistry meterRegistry;
    private final SubmissionResultOutboxWriter resultOutboxWriter;
    private final SubmissionCreatedOutboxWriter createdOutboxWriter;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    private static final List<String> SUPPORTED_LANGUAGES = List.of(
            "javascript", "python", "java", "c", "cpp"
    );

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                "Submission facts snapshot is required");
    }

    @Override
    public SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO) {
        throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                "Submission facts snapshot is required");
    }

    @Override
    @Transactional
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO,
                               SubmissionFactsSnapshot facts) {
        if (createDTO != null && (StringUtils.hasText(createDTO.getContestId())
                || StringUtils.hasText(createDTO.getVirtualSessionId()))) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Contest submission requires the contest command");
        }
        return submitInternal(userId, createDTO, facts, false);
    }

    @Override
    @Transactional
    public SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO,
                                      SubmissionFactsSnapshot facts) {
        if (createDTO == null || !StringUtils.hasText(createDTO.getContestId())) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Contest context is required");
        }
        return submitInternal(userId, createDTO, facts, true);
    }

    private SubmissionVO submitInternal(String userId, CreateSubmissionDTO createDTO,
                                        SubmissionFactsSnapshot facts,
                                        boolean contestCommand) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST);
        }
        if (createDTO == null || !StringUtils.hasText(createDTO.getCode())
                || !StringUtils.hasText(createDTO.getLanguage())) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST);
        }
        String language = createDTO.getLanguage().toLowerCase();
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST);
        }
        if (facts == null || !facts.admits(userId, createDTO.getProblemId())) {
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

        if (contestCommand) {
            long generation = submission.getGeneration() != null ? submission.getGeneration() : 1L;
            createdOutboxWriter.recordSubmissionCreated(
                    submission.getId(), generation, userId,
                    String.valueOf(createDTO.getProblemId()), createDTO.getContestId(),
                    createDTO.getVirtualSessionId(), language, submission.getCreatedAt());
        }

        if (portActive) {
            log.debug("Submit {} skipped legacy RQueue (port cutover active)", submission.getId());
        } else {
            log.warn("Submit {}: legacy judge enqueue is not supported by "
                    + "backend-submission; keep useJudgeOutbox+usePort active", submission.getId());
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
        submission.setTestDetails(toEntityDetails(TestCaseDetailCodec.fromJson(testDetailsJson)));
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
        // SPLIT-003 slice-2: there is no local event consumer in this owner.
        // The result outbox is the only durable cross-service channel, so a
        // terminal verdict is ALWAYS recorded there (the App adapter only
        // writes it as a fallback when the in-process event publish fails).
        // The result-outbox dispatcher (later slice) forwards it to
        // Notification/Achievement/WebSocket consumers.
        if (status.isTerminal()) {
            try {
                resultOutboxWriter.recordVerdictResult(
                        submission.getId(),
                        generation > 0 ? generation : 1L,
                        submission.getUserId(),
                        String.valueOf(submission.getProblemId()),
                        SubmissionStatusCodec.toWire(status),
                        runtimeMs,
                        memoryMb,
                        contestId);
            } catch (Exception e) {
                log.error("Failed to record result outbox for submission {}: {}",
                        submission.getId(), e.getMessage());
            }
        }
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

    private List<Submission.TestCaseDetail> toEntityDetails(List<SubmissionTestCaseDetailDTO> details) {
        if (details == null) {
            return null;
        }
        return details.stream().map(detail -> {
            Submission.TestCaseDetail entity = new Submission.TestCaseDetail();
            entity.setStatus(detail.status());
            entity.setTime(detail.time());
            entity.setMemory(detail.memory());
            entity.setDetail(detail.detail());
            entity.setOutput(detail.output());
            entity.setExpectedOutput(detail.expectedOutput());
            entity.setCaseId(detail.caseId());
            entity.setCaseScope(detail.caseScope());
            entity.setInputs(detail.inputs().stream().map(input -> {
                Submission.TestCaseDetail.InputParam entityInput =
                        new Submission.TestCaseDetail.InputParam();
                entityInput.setId(input.id());
                entityInput.setLabel(input.label());
                entityInput.setName(input.name());
                entityInput.setValue(input.value());
                return entityInput;
            }).toList());
            return entity;
        }).toList();
    }
}
