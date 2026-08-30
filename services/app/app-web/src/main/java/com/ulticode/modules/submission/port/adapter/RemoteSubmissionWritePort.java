package com.ulticode.modules.submission.port.adapter;

import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.UserExistencePort;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionIntakePort;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** App intake adapter; every mutation is executed by backend-submission. */
@Component
@Primary
@RequiredArgsConstructor
public class RemoteSubmissionWritePort implements SubmissionIntakePort {

    private final ProblemFactsPort problemFacts;
    private final UserExistencePort userExistencePort;

    @DubboReference(group = "backend-submission", version = "1.0.0", timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private SubmissionIntakePort submissionIntake;


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
