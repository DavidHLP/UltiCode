package com.ulticode.modules.submission.port.adapter;

import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.UserExistencePort;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionIntakePort;
import com.ulticode.submission.api.service.SubmissionVerdictWritePort;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Remote App route used only when Submission owner cutover is enabled. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.submission.routing", name = "mode", havingValue = "remote")
public class RemoteSubmissionWritePort implements SubmissionIntakePort, SubmissionVerdictWritePort {

    private final ProblemFactsPort problemFacts;
    private final UserExistencePort userExistencePort;

    @DubboReference(group = "backend-submission", version = "1.0.0", timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private SubmissionIntakePort submissionIntake;

    @DubboReference(group = "backend-submission", version = "1.0.0", timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private SubmissionVerdictWritePort submissionVerdict;

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        return submit(userId, createDTO, captureFacts(userId, createDTO));
    }

    @Override
    public SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO) {
        return submitContest(userId, createDTO, captureFacts(userId, createDTO));
    }

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO,
                               SubmissionFactsSnapshot facts) {
        return submissionIntake.submit(userId, createDTO, facts);
    }

    @Override
    public SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO,
                                      SubmissionFactsSnapshot facts) {
        return submissionIntake.submitContest(userId, createDTO, facts);
    }

    @Override
    public void updateSubmissionResult(String submissionId, SubmissionStatus status,
                                       int runtime, Double memory, String testDetailsJson) {
        submissionVerdict.updateSubmissionResult(submissionId, status, runtime, memory, testDetailsJson);
    }

    @Override
    public boolean updateSubmissionResultFenced(String submissionId, SubmissionStatus status,
                                                int runtime, Double memory, String testDetailsJson,
                                                long generation, String attemptId) {
        return submissionVerdict.updateSubmissionResultFenced(
                submissionId, status, runtime, memory, testDetailsJson, generation, attemptId);
    }

    private SubmissionFactsSnapshot captureFacts(String userId, CreateSubmissionDTO createDTO) {
        if (!StringUtils.hasText(userId) || createDTO == null
                || !StringUtils.hasText(createDTO.getCode())
                || !StringUtils.hasText(createDTO.getLanguage())
                || createDTO.getProblemId() == null) {
            return null;
        }
        Long problemId = createDTO == null ? null : createDTO.getProblemId();
        String language = createDTO == null ? null : createDTO.getLanguage();
        ProblemFactsPort.ProblemDisplayFacts display = problemFacts.findDisplayFacts(problemId);
        ProblemFactsPort.ProblemLimits limits = problemFacts.findLimits(problemId);
        return new SubmissionFactsSnapshot(
                userId,
                userExistencePort.existsById(userId),
                display == null ? null : new SubmissionFactsSnapshot.ProblemFacts(
                        display.id(), display.title(), display.slug(),
                        limits == null ? null : limits.timeLimitSeconds(),
                        limits == null ? null : limits.memoryLimitMb(),
                        problemFacts.findStarterCode(problemId, language)),
                System.currentTimeMillis(),
                SubmissionFactsSnapshot.CURRENT_SCHEMA_VERSION);
    }
}
