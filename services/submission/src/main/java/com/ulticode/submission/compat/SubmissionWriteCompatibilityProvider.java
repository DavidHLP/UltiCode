package com.ulticode.submission.compat;

import com.ulticode.app.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.app.api.service.SubmissionWritePort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.context.annotation.Profile;

/**
 * Transitional Submission owner provider.
 *
 * <p>The provider is the only new network seam in SPLIT-002. It delegates to
 * the current App writer, so there is still exactly one storage writer while
 * SPLIT-003 performs the expand/backfill/cutover. It intentionally imports
 * only app-api DTOs and ports, never an App entity or mapper.
 */
@DubboService(group = "backend-submission", version = "1.0.0")
@Profile("!test")
public class SubmissionWriteCompatibilityProvider implements SubmissionWritePort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = 10000, retries = 0, check = false)
    private SubmissionWritePort appWriter;

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        return appWriter.submit(userId, createDTO);
    }

    @Override
    public void updateSubmissionResult(String submissionId, SubmissionStatus status,
                                       int runtime, Double memory, String testDetailsJson) {
        appWriter.updateSubmissionResult(submissionId, status, runtime, memory, testDetailsJson);
    }

    @Override
    public boolean updateSubmissionResultFenced(String submissionId, SubmissionStatus status,
                                                int runtime, Double memory, String testDetailsJson,
                                                long generation, String attemptId) {
        return appWriter.updateSubmissionResultFenced(
                submissionId, status, runtime, memory, testDetailsJson, generation, attemptId);
    }
}
