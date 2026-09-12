package com.ulticode.modules.submission.port;

import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.UserExistencePort;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Captures the App-owned admission facts required by Submission intake. */
@Component
@RequiredArgsConstructor
public class SubmissionFactsCapture {

    private final ProblemFactsPort problemFacts;
    private final UserExistencePort userExistencePort;

    /**
     * Capture one immutable snapshot for a submission request.
     *
     * <p>Malformed input fails at the App boundary with a typed business
     * error. A valid-shaped request for a missing problem still produces a
     * snapshot that the Submission owner rejects as not found.
     */
    public SubmissionFactsSnapshot capture(String userId, CreateSubmissionDTO createDTO) {
        if (!StringUtils.hasText(userId)) {
            throw invalid("User id is required");
        }
        if (createDTO == null) {
            throw invalid("Submission payload is required");
        }
        if (!StringUtils.hasText(createDTO.getCode())) {
            throw invalid("Code cannot be empty");
        }
        if (!StringUtils.hasText(createDTO.getLanguage())) {
            throw invalid("Language is required");
        }
        if (createDTO.getProblemId() == null) {
            throw invalid("Problem ID is required");
        }

        Long problemId = createDTO.getProblemId();
        String language = createDTO.getLanguage();
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

    private static BusinessException invalid(String message) {
        return new BusinessException(BaseErrorCode.BAD_REQUEST, message);
    }
}
